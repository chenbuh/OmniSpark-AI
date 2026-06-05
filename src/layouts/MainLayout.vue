<template>
  <n-layout has-sider class="app-layout">
    <!-- 左侧侧边栏 -->
    <n-layout-sider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="260"
      show-trigger="arrow-circle"
      content-style="display: flex; flex-direction: column; height: 100%;"
      :native-scrollbar="false"
      class="glass-sider"
    >
      <!-- Logo 区域 -->
      <div class="logo-area" :class="{ 'collapsed': collapsed }">
        <div class="logo-icon-box">
          <Zap class="logo-icon" />
        </div>
        <span class="logo-text" v-if="!collapsed">OmniSpark AI</span>
      </div>

      <!-- 侧边导航菜单 -->
      <div class="menu-box">
        <n-menu
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions"
          :value="activeKey"
          @update:value="handleMenuSelect"
        />
      </div>


    </n-layout-sider>

    <!-- 右侧主布局 -->
    <n-layout class="main-layout-content">
      <!-- 顶部导航栏 -->
      <n-layout-header bordered class="glass-header">
        <div class="header-left">
          <!-- 项目空间切换 -->
          <span class="proj-label">项目空间:</span>
          <n-select
            v-model:value="projectStore.activeProjectId"
            :options="projectOptions"
            :placeholder="projectSelectorPlaceholder"
            :disabled="projectSelectorDisabled"
            class="project-selector"
            size="medium"
            @update:value="handleProjectChange"
          />
          <span v-if="projectSelectorStatusText" class="project-status-note">{{ projectSelectorStatusText }}</span>
          <n-button type="primary" size="medium" secondary class="add-proj-btn" @click="showAddProjectModal = true">
            <template #icon>
              <Plus />
            </template>
            新建项目
          </n-button>
          <n-button type="primary" size="medium" secondary class="share-proj-btn" @click="showShareModal = true" :disabled="!projectStore.activeProjectId">
            <template #icon>
              <Share2 />
            </template>
            共享
          </n-button>
          <n-dropdown :options="projectActionOptions" @select="handleProjectAction">
            <n-button type="primary" size="medium" secondary>
              <template #icon><MoreVertical /></template>
            </n-button>
          </n-dropdown>
        </div>

        <div class="header-right">
          <!-- 通知中心 -->
          <n-popover trigger="click" placement="bottom-end" :width="360">
            <template #trigger>
              <n-badge :value="unreadBadgeValue" :max="99">
                <n-button circle secondary class="notify-btn">
                  <template #icon>
                    <Bell />
                  </template>
                </n-button>
              </n-badge>
            </template>

            <div class="notif-popover">
              <div class="notif-header">
                <span class="notif-title">通知 ({{ unreadCountLabel }})</span>
                <n-button v-if="hasUnreadNotifications" size="tiny" text type="primary" @click="handleMarkAllRead">
                  全部已读
                </n-button>
              </div>
              <div class="notif-list" v-if="notifications && notifications.length > 0">
                <div
                  v-for="n in notifications.slice(0, 10)"
                  :key="n.id"
                  class="notif-item"
                  :class="{ 'notif-unread': !n.isRead }"
                  @click="handleNotificationClick(n)"
                >
                  <div class="notif-dot" :class="n.type"></div>
                  <div class="notif-body">
                    <span class="notif-item-title">{{ n.title }}</span>
                    <span class="notif-content">{{ n.content }}</span>
                    <div class="notif-footer">
                      <span class="notif-time">{{ n.createdAt?.substring(5, 19)?.replace('T', ' ') }}</span>
                      <span class="notif-view">查看详情 →</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="notif-empty" v-else-if="notifications !== null">暂无通知</div>
              <div class="notif-empty" v-else>通知列表待确认</div>
            </div>
          </n-popover>

          <!-- 用户头像下拉 -->
          <n-dropdown :options="userDropdownOptions" @select="handleUserDropdownSelect">
            <div class="avatar-box">
              <n-avatar round size="medium" :src="userStore.userInfo?.avatar" />
              <div class="user-info-text">
                <span class="username">{{ userStore.userInfo?.nickname }}</span>
                <span class="role-badge" :class="userStore.userInfo?.role">{{ formatUserRole(userStore.userInfo?.role) }}</span>
              </div>
            </div>
          </n-dropdown>
        </div>
      </n-layout-header>

      <!-- 核心页面视图 -->
      <n-layout-content class="page-content-wrapper">
        <router-view />
      </n-layout-content>
    </n-layout>

    <!-- 新建项目弹窗 -->
    <n-modal
      v-model:show="showAddProjectModal"
      preset="dialog"
      title="创建新项目"
      positive-text="确认创建"
      negative-text="取消"
      @positive-click="handleAddProject"
    >
      <n-form :model="addProjectForm" style="margin-top: 15px;">
        <n-form-item label="项目名称">
          <n-input v-model:value="addProjectForm.name" placeholder="请输入项目名称..." />
        </n-form-item>
        <n-form-item label="项目描述">
          <n-input
            v-model:value="addProjectForm.description"
            type="textarea"
            placeholder="简述该项目的主要生成目标与规划..."
          />
        </n-form-item>
      </n-form>
    </n-modal>

    <!-- 项目共享弹窗 -->
    <n-modal v-model:show="showShareModal" preset="card" title="项目共享设置" style="width: 480px;" closable>
      <div v-if="projectStore.activeProjectId">
        <div class="share-info-line">
          <span>当前项目:</span>
          <n-tag type="info" size="small">{{ currentProjectName }}</n-tag>
        </div>
        <div v-if="sharesLoadState === 'error'" class="share-status-note">共享列表待确认，请稍后重试。</div>
        <div v-if="teamsLoadState === 'error'" class="share-status-note">团队选项待确认，请稍后重试。</div>

        <!-- 已有共享列表 -->
        <div class="share-list" v-if="shares && shares.length > 0">
          <div class="share-item" v-for="s in shares" :key="s.id">
            <span class="share-team-name">{{ s.teamName }}</span>
            <n-select
              :value="s.permission"
              :options="permissionOptions"
              size="small"
              style="width: 110px;"
              @update:value="(val: string) => handleUpdateShare(s.id, val)"
            />
            <n-button size="tiny" type="error" tertiary @click="handleRemoveShare(s.id)">
              取消共享
            </n-button>
          </div>
        </div>
        <n-empty v-else-if="shares !== null" description="暂无共享" style="padding: 16px 0;" />
        <n-empty v-else description="共享列表待确认，请稍后重试。" style="padding: 16px 0;" />

        <!-- 添加共享 -->
        <n-divider />
        <div class="add-share-row">
          <n-select
            v-model:value="newShareTeamId"
            :options="teamOptions"
            :placeholder="shareTeamPlaceholder"
            :disabled="teamsLoadState === 'error' || teamOptions.length === 0"
            style="flex: 1;"
          />
          <n-select
            v-model:value="newSharePermission"
            :options="permissionOptions"
            style="width: 110px;"
          />
          <n-button type="primary" size="small" @click="handleAddShare">添加</n-button>
        </div>
      </div>
    </n-modal>

    <n-modal
      v-model:show="showDeleteProjectModal"
      preset="dialog"
      title="删除项目空间"
      positive-text="确认删除"
      negative-text="取消"
      @positive-click="handleDeleteProject"
    >
      <div class="delete-project-dialog">
        <p>确定要删除项目空间“{{ currentProjectName || '未命名项目' }}”吗？</p>
        <p>删除后将同时清理该项目下的图片、视频、参考素材，以及关联的任务、工作流、模板和模型配置，且无法恢复。</p>
      </div>
    </n-modal>
  </n-layout>
</template>

<script setup lang="ts">
import { ref, computed, h, reactive, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage, NIcon } from 'naive-ui'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { useProjectStore } from '@/store/project'
import { useTeamStore } from '@/store/team'
import { projectShareApi } from '@/api/projectShares'
import { providerApi } from '@/api/providers'
import request, { API_BASE_URL } from '@/api/request'
import { templateApi } from '@/api/templates'
import { styleCardApi } from '@/api/styleCards'
import { workflowApi } from '@/api/workflows'
import { formatUserRole } from '@/utils/role'
import {
  LayoutDashboard, Image, Video, ClipboardList, Library,
  Layers, BookOpen, BarChart3, Plus, Bell, Zap,
  Users, Share2, Film, GitBranch, Shield, MoreVertical,
  Globe, User, Activity
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const message = useMessage()

const userStore = useUserStore()
const projectStore = useProjectStore()
const teamStore = useTeamStore()

const collapsed = ref(false)
const showAddProjectModal = ref(false)
const showDeleteProjectModal = ref(false)

const addProjectForm = reactive({
  name: '',
  description: ''
})

// 项目共享状态
const showShareModal = ref(false)
const shares = ref<any[] | null>(null)
const newShareTeamId = ref<number | null>(null)
const newSharePermission = ref('view')
const sharesLoadState = ref<'loading' | 'ready' | 'error'>('ready')
const teamsLoadState = ref<'loading' | 'ready' | 'error'>('ready')
const permissionOptions = [
  { label: '查看', value: 'view' },
  { label: '编辑', value: 'edit' },
  { label: '管理', value: 'admin' }
]

const currentProjectName = computed(() => {
  const p = projectStore.projects.find(p => p.id === projectStore.activeProjectId)
  return p?.name || ''
})

function requireProjectShare(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('共享结果待确认')
  }
  const id = Number((value as any).id)
  const projectId = Number((value as any).projectId)
  const teamId = Number((value as any).teamId)
  const permission = typeof (value as any).permission === 'string' ? (value as any).permission.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !Number.isFinite(projectId) || projectId <= 0 || !Number.isFinite(teamId) || teamId <= 0 || !permission) {
    throw new Error('共享结果待确认')
  }
  return {
    id,
    projectId,
    teamId,
    permission
  }
}

function requireImportedProjectId(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error('项目导入结果待确认')
  }
  return parsed
}

function requireExportPayload(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('项目导出结果待确认')
  }
  const payload = value as Record<string, any>
  const version = typeof payload.version === 'string' ? payload.version.trim() : ''
  const exportedAt = typeof payload.exportedAt === 'string' ? payload.exportedAt.trim() : ''
  const project = payload.project
  if (!version || !exportedAt || !project || typeof project !== 'object' || Array.isArray(project)) {
    throw new Error('项目导出结果待确认')
  }
  const projectId = Number((project as Record<string, any>).id)
  const projectName = typeof (project as Record<string, any>).name === 'string'
    ? (project as Record<string, any>).name.trim()
    : ''
  const projectDescription = typeof (project as Record<string, any>).description === 'string'
    ? (project as Record<string, any>).description.trim()
    : ''
  if (!Number.isFinite(projectId) || projectId <= 0 || !projectName) {
    throw new Error('项目导出结果待确认')
  }
  const providers = Array.isArray(payload.providers) ? payload.providers : null
  const promptTemplates = Array.isArray(payload.promptTemplates) ? payload.promptTemplates : null
  const styleCards = Array.isArray(payload.styleCards) ? payload.styleCards : null
  const workflows = Array.isArray(payload.workflows) ? payload.workflows : null
  const assets = Array.isArray(payload.assets) ? payload.assets : null
  if (!providers || !promptTemplates || !styleCards || !workflows || !assets) {
    throw new Error('项目导出结果待确认')
  }
  return {
    ...payload,
    version,
    exportedAt,
    canaryToken: typeof payload.canaryToken === 'string' ? payload.canaryToken : '',
    project: {
      ...(project as Record<string, any>),
      id: projectId,
      name: projectName,
      description: projectDescription
    },
    providers,
    promptTemplates,
    styleCards,
    workflows,
    assets
  }
}

function requireImportPayload(value: unknown) {
  const payload = requireExportPayload(value)
  if (typeof payload.canaryToken !== 'string' && payload.canaryToken != null) {
    throw new Error('导入文件内容待确认')
  }
  return payload
}

function requireImportedProjectRecord(
  value: unknown,
  expected: { name: string; description: string }
) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('项目导入结果待确认')
  }
  const record = value as Record<string, unknown>
  const id = Number(record.id)
  const name = typeof record.name === 'string' ? record.name.trim() : ''
  const description = typeof record.description === 'string' ? record.description.trim() : ''
  if (!Number.isFinite(id) || id <= 0) {
    throw new Error('项目导入结果待确认')
  }
  if (name !== expected.name || description !== expected.description) {
    throw new Error('项目导入结果待确认')
  }
  return {
    id,
    name,
    description
  }
}

function requirePageTotal(value: unknown, errorMessage: string) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const total = Number((value as Record<string, unknown>).total)
  if (!Number.isFinite(total) || total < 0) {
    throw new Error(errorMessage)
  }
  return total
}

async function verifyImportedProjectData(
  projectId: number,
  payload: ReturnType<typeof requireImportPayload>
) {
  await projectStore.refresh()
  const expectedProjectName = `${payload.project.name} (导入)`
  const expectedProjectDescription = typeof payload.project.description === 'string'
    ? payload.project.description.trim()
    : ''
  const importedProject = requireImportedProjectRecord(
    projectStore.projects.find(project => project.id === projectId),
    {
      name: expectedProjectName,
      description: expectedProjectDescription
    }
  )
  const [providersRes, templatesRes, styleCardsRes, workflowsRes] = await Promise.all([
    providerApi.getProviders(projectId),
    templateApi.getTemplates({ projectId, page: 1, pageSize: 1 }),
    styleCardApi.list({ projectId, page: 1, pageSize: 1 }),
    workflowApi.list(projectId)
  ])
  const providers = Array.isArray((providersRes as any).data) ? (providersRes as any).data : null
  const templateTotal = requirePageTotal((templatesRes as any).data, '项目导入结果待确认')
  const styleCardTotal = requirePageTotal((styleCardsRes as any).data, '项目导入结果待确认')
  const workflows = Array.isArray((workflowsRes as any).data) ? (workflowsRes as any).data : null
  if (!providers || !workflows) {
    throw new Error('项目导入结果待确认')
  }
  if (providers.length !== payload.providers.length) {
    throw new Error('项目导入结果待确认')
  }
  if (templateTotal !== payload.promptTemplates.length) {
    throw new Error('项目导入结果待确认')
  }
  if (styleCardTotal !== payload.styleCards.length) {
    throw new Error('项目导入结果待确认')
  }
  if (workflows.length !== payload.workflows.length) {
    throw new Error('项目导入结果待确认')
  }
  return importedProject
}

function normalizeNotificationItem(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('通知数据待确认')
  }
  const item = value as Record<string, any>
  const id = Number(item.id)
  const title = typeof item.title === 'string' ? item.title.trim() : ''
  const content = typeof item.content === 'string' ? item.content.trim() : ''
  const type = typeof item.type === 'string' ? item.type.trim() : ''
  const isRead = normalizeNotificationReadFlag(item.isRead)
  if (!Number.isFinite(id) || id <= 0 || !title || !content || !type || isRead === null) {
    throw new Error('通知数据待确认')
  }
  return {
    ...item,
    id,
    title,
    content,
    type,
    isRead,
    relatedId: item.relatedId == null || item.relatedId === '' ? null : Number(item.relatedId),
    createdAt: typeof item.createdAt === 'string' ? item.createdAt : ''
  }
}

function normalizeNotificationList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('通知数据待确认')
  }
  return value.map(item => normalizeNotificationItem(item))
}

const teamOptions = computed(() => {
  return teamStore.teams.map(t => ({ label: t.name, value: t.id }))
})
const shareTeamPlaceholder = computed(() => {
  if (teamsLoadState.value === 'error') {
    return '团队选项待确认'
  }
  if (teamsLoadState.value === 'loading') {
    return '团队选项加载中'
  }
  if (teamOptions.value.length === 0) {
    return '暂无可共享团队'
  }
  return '选择要共享的团队'
})

// 当前激活的菜单项
const activeKey = computed(() => {
  const path = route.path
  if (path.startsWith('/generate/image')) return 'generate-image'
  if (path.startsWith('/generate/video')) return 'generate-video'
  if (path.startsWith('/admin/dashboard')) return 'admin-dashboard'
  if (path.startsWith('/admin/tasks')) return 'admin-tasks'
  if (path.startsWith('/admin/assets')) return 'admin-assets'
  if (path.startsWith('/admin/users')) return 'admin-users'
  if (path.startsWith('/admin/monitor')) return 'admin-monitor'
  if (path.startsWith('/admin/update')) return 'admin-update'
  if (path.startsWith('/admin/dict')) return 'admin-dict'
  if (path.startsWith('/admin/files')) return 'admin-files'
  if (path.startsWith('/admin/scheduled-tasks')) return 'admin-scheduled-tasks'
  if (path.startsWith('/admin/webhooks')) return 'admin-webhooks'
  if (path.startsWith('/admin/logs')) return 'admin-logs'
  if (path.startsWith('/admin/maintenance')) return 'admin-maintenance'
  if (path.startsWith('/admin/announcements')) return 'admin-announcements'
  if (path.startsWith('/admin/cleanup')) return 'admin-cleanup'
  if (path.startsWith('/admin/login-logs')) return 'admin-login-logs'
  if (path.startsWith('/admin/config')) return 'admin-config'
  return path.substring(1) || 'dashboard'
})

// 项目下拉列表
const projectOptions = computed(() => {
  return projectStore.projects.map(p => ({
    label: p.name,
    value: p.id
  }))
})
const projectSelectorPlaceholder = computed(() => {
  if (projectStore.loadState === 'error') {
    return '项目列表待确认'
  }
  if (projectStore.loadState === 'loading') {
    return '项目空间加载中'
  }
  if (projectStore.loadState === 'ready' && projectOptions.value.length === 0) {
    return '暂无项目空间'
  }
  return '选择项目空间'
})
const projectSelectorDisabled = computed(() => {
  return projectStore.loadState === 'error' || (projectStore.loadState === 'ready' && projectOptions.value.length === 0)
})
const projectSelectorStatusText = computed(() => {
  if (projectStore.loadState === 'error') {
    return '项目列表待确认'
  }
  if (projectStore.loadState === 'ready' && projectOptions.value.length === 0) {
    return '暂无项目空间'
  }
  return ''
})

// 侧边栏菜单配置
const renderIcon = (icon: any) => {
  return () => h(NIcon, null, { default: () => h(icon) })
}

const menuOptions = computed(() => {
  const items: any[] = [
    { label: '控制台', key: 'dashboard', icon: renderIcon(LayoutDashboard) },
    { label: '文生/图生图', key: 'generate-image', icon: renderIcon(Image) },
    { label: '生视频中心', key: 'generate-video', icon: renderIcon(Video) },
    { label: '任务中心', key: 'tasks', icon: renderIcon(ClipboardList) },
    { label: '共享资产库', key: 'assets', icon: renderIcon(Library) },
    { label: '社区共享', key: 'community', icon: renderIcon(Globe) },
    { label: '模型配置中心', key: 'model-providers', icon: renderIcon(Layers) },
    { label: '提示词模板', key: 'prompt-templates', icon: renderIcon(BookOpen) },
    { label: '工作流编排', key: 'workflows', icon: renderIcon(GitBranch) },
    { label: '角色/风格卡', key: 'style-cards', icon: renderIcon(Film) },
    { label: '用量统计分析', key: 'stats', icon: renderIcon(BarChart3) },
    { label: '团队管理', key: 'teams', icon: renderIcon(Users) },
    { label: '审计日志', key: 'audit-logs', icon: renderIcon(Activity) },
    { label: '个人设置', key: 'settings', icon: renderIcon(User) }
  ]
  // 管理员额外显示管理中心
  if (userStore.userInfo?.role === 'admin') {
    items.splice(items.length - 1, 0, { type: 'divider' as const, key: 'admin-divider', label: '系统' })
    items.splice(items.length - 1, 0, { label: '管理中心', key: 'admin-dashboard', icon: renderIcon(Shield) })
  }
  return items
})

// 用户下拉菜单
const userDropdownOptions = computed(() => [
  { label: (window as any).__isDark?.value ? '☀️ 亮色模式' : '🌙 暗黑模式', key: 'toggle-theme' },
  { label: '系统设置', key: 'settings' },
  { label: '退出登录', key: 'logout' }
])

// 切换路由
const routeMap: Record<string, string> = {
  'generate-image': '/generate/image',
  'generate-video': '/generate/video',
  'admin-dashboard': '/admin/dashboard',
  'admin-tasks': '/admin/tasks',
  'admin-assets': '/admin/assets',
  'admin-users': '/admin/users',
  'admin-monitor': '/admin/monitor',
  'admin-update': '/admin/update',
  'admin-dict': '/admin/dict',
  'admin-files': '/admin/files',
  'admin-scheduled-tasks': '/admin/scheduled-tasks',
  'admin-webhooks': '/admin/webhooks',
  'admin-logs': '/admin/logs',
  'admin-maintenance': '/admin/maintenance',
  'admin-announcements': '/admin/announcements',
  'admin-cleanup': '/admin/cleanup',
  'admin-login-logs': '/admin/login-logs',
  'admin-config': '/admin/config'
}

const handleMenuSelect = (key: string) => {
  router.push(routeMap[key] || `/${key}`)
}

// 切换项目空间时，给出反馈
const handleProjectChange = (val: number) => {
  projectStore.setActiveProject(val)
  const proj = projectStore.projects.find(p => p.id === val)
  message.info(`已切换至空间: ${proj?.name}`)
}

// 新建项目
const handleAddProject = async () => {
  if (!addProjectForm.name) {
    message.error('项目名称不能为空')
    return false
  }
  try {
    const createdProject = await projectStore.addProject(addProjectForm.name, addProjectForm.description)
    const createdId = Number((createdProject as any)?.id ?? projectStore.activeProjectId)
    if (!Number.isFinite(createdId) || createdId <= 0 || !projectStore.projects.some(project => project.id === createdId)) {
      throw new Error('项目创建结果待确认')
    }
    message.success('新项目空间创建成功！')
    addProjectForm.name = ''
    addProjectForm.description = ''
    showAddProjectModal.value = false
    return true
  } catch (err: any) {
    message.error(err.message || '项目创建失败')
    return false
  }
}

// 打开共享弹窗时加载数据
watch(showShareModal, async (val: boolean) => {
  if (val) {
    await loadShareModalData()
  }
})

const loadShareModalData = async () => {
  await Promise.allSettled([
    loadTeamOptions(),
    loadShares()
  ])
}

const loadTeamOptions = async () => {
  teamsLoadState.value = 'loading'
  try {
    const teams = await teamStore.refresh()
    if (!Array.isArray(teams)) {
      throw new Error('团队选项待确认')
    }
    teamsLoadState.value = 'ready'
    if (newShareTeamId.value && !teamStore.teams.some(team => team.id === newShareTeamId.value)) {
      newShareTeamId.value = null
    }
  } catch {
    teamsLoadState.value = 'error'
    newShareTeamId.value = null
  }
}

const loadShares = async () => {
  if (!projectStore.activeProjectId) return
  sharesLoadState.value = 'loading'
  try {
    const res = await projectShareApi.getShares(projectStore.activeProjectId)
    if (!Array.isArray(res.data)) {
      throw new Error('共享列表待确认')
    }
    shares.value = res.data
    sharesLoadState.value = 'ready'
  } catch {
    shares.value = null
    sharesLoadState.value = 'error'
  }
}

const handleAddShare = async () => {
  if (teamsLoadState.value === 'error') {
    message.error('团队选项待确认，请稍后重试')
    return
  }
  if (!newShareTeamId.value || !projectStore.activeProjectId) return
  try {
    const activeProjectId = projectStore.activeProjectId
    const shareTeamId = newShareTeamId.value
    const sharePermission = newSharePermission.value
    const res = await projectShareApi.createShare({
      projectId: activeProjectId,
      teamId: shareTeamId,
      permission: sharePermission
    })
    const created = requireProjectShare((res as any).data)
    await loadShares()
    const createdShare = shares.value?.find(share => Number(share.id) === created.id)
      || shares.value?.find(share =>
        Number(share.projectId) === activeProjectId
        && Number(share.teamId) === shareTeamId
        && String(share.permission) === sharePermission
      )
    if (!createdShare || Number(createdShare.projectId) !== activeProjectId || Number(createdShare.teamId) !== shareTeamId || String(createdShare.permission) !== sharePermission) {
      throw new Error('共享结果待确认')
    }
    message.success('共享成功')
    newShareTeamId.value = null
  } catch (err: any) {
    message.error(err.message || '共享失败')
  }
}

const handleUpdateShare = async (shareId: number, permission: string) => {
  try {
    const currentShare = shares.value?.find(share => Number(share.id) === shareId)
    const expectedProjectId = Number(currentShare?.projectId || projectStore.activeProjectId)
    const expectedTeamId = Number(currentShare?.teamId)
    const res = await projectShareApi.updatePermission(shareId, permission)
    requireProjectShare((res as any).data)
    await loadShares()
    const updatedShare = shares.value?.find(share => Number(share.id) === shareId)
    if (!updatedShare || Number(updatedShare.projectId) !== expectedProjectId || Number(updatedShare.teamId) !== expectedTeamId || String(updatedShare.permission) !== permission) {
      throw new Error('共享权限待确认')
    }
    message.success('权限已更新')
  } catch (err: any) {
    message.error(err.message || '更新失败')
  }
}

const handleRemoveShare = async (shareId: number) => {
  try {
    await projectShareApi.removeShare(shareId)
    await loadShares()
    if (shares.value?.some(share => Number(share.id) === shareId)) {
      throw new Error('取消共享结果待确认')
    }
    message.success('已取消共享')
  } catch (err: any) {
    message.error(err.message || '操作失败')
  }
}

// ===== 项目操作菜单 =====
const projectActionOptions = [
  { label: '📤 导出项目数据', key: 'export' },
  { label: '📥 导入项目数据', key: 'import' },
  { label: '🗑 删除项目空间', key: 'delete' }
]

const handleProjectAction = async (key: string) => {
  if (key === 'export') {
    await handleExportProject()
  } else if (key === 'delete') {
    if (!projectStore.activeProjectId) {
      message.error('请先选择一个项目')
      return
    }
    showDeleteProjectModal.value = true
  } else if (key === 'import') {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.json'
    input.onchange = async (e: any) => {
        const file = e.target?.files?.[0]
        if (!file) return
        try {
          const text = await file.text()
          const data = requireImportPayload(JSON.parse(text))
          const res = await request.post('/api/projects/import', data)
          const importedProjectId = requireImportedProjectId((res as any).data?.projectId)
          const importedProject = await verifyImportedProjectData(importedProjectId, data)
          projectStore.setActiveProject(importedProject.id)
          message.success('项目导入成功！')
      } catch (err: any) {
        message.error('导入失败: ' + (err.message || '文件格式错误'))
      }
    }
    input.click()
  }
}

const handleDeleteProject = async () => {
  if (!projectStore.activeProjectId) {
    message.error('请先选择一个项目')
    return false
  }
  try {
    const deletingProjectId = projectStore.activeProjectId
    const deletingProjectName = currentProjectName.value || `项目 ${projectStore.activeProjectId}`
    await projectStore.deleteProject(deletingProjectId)
    if (projectStore.projects.some(project => project.id === deletingProjectId)) {
      throw new Error('项目删除结果待确认')
    }
    showDeleteProjectModal.value = false
    message.success(`项目空间“${deletingProjectName}”已删除`)
    if (route.path.startsWith('/admin/')) {
      await router.push('/admin/dashboard')
    }
  } catch (err: any) {
    message.error(err.message || '删除项目失败')
    return false
  }
}

const handleExportProject = async () => {
  if (!projectStore.activeProjectId) {
    message.error('请先选择一个项目')
    return
  }
  try {
    const res = await request.post(`/api/projects/${projectStore.activeProjectId}/export`)
    const exportPayload = requireExportPayload((res as any).data)
    if (exportPayload.project.id !== projectStore.activeProjectId) {
      throw new Error('项目导出结果待确认')
    }
    const blobContent = JSON.stringify(exportPayload, null, 2)
    if (!blobContent.trim()) {
      throw new Error('项目导出结果待确认')
    }
    const blob = new Blob([blobContent], { type: 'application/json' })
    if (blob.size <= 0) {
      throw new Error('项目导出结果待确认')
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `project_${projectStore.activeProjectId}_${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    message.success('项目导出成功！')
  } catch (err: any) {
    message.error('导出失败: ' + (err.message || '网络错误'))
  }
}

// ===== 通知系统 =====
const notifications = ref<any[] | null>(null)
const unreadCount = ref<number | null>(null)
let stompClient: any = null
let notificationPollTimer: ReturnType<typeof setInterval> | null = null
const NOTIFICATION_HEADERS = { 'X-No-Cache': '1' }
let notificationErrorNotified = false

const unreadBadgeValue = computed(() => unreadCount.value == null ? false : unreadCount.value)
const unreadCountLabel = computed(() => unreadCount.value == null ? '未读待确认' : `${unreadCount.value} 未读`)
const hasUnreadNotifications = computed(() => (unreadCount.value ?? 0) > 0)

const loadNotifications = async () => {
  try {
    const [unreadRes, allRes] = await Promise.all([
      request.get('/api/notifications/unread', {
        params: { limit: 50 },
        headers: NOTIFICATION_HEADERS
      }),
      request.get('/api/notifications', {
        params: { limit: 20 },
        headers: NOTIFICATION_HEADERS
      })
    ])
    const unreadItems = normalizeNotificationList((unreadRes as any).data)
    const allItems = normalizeNotificationList((allRes as any).data)
    unreadCount.value = unreadItems.length
    notifications.value = allItems
    notificationErrorNotified = false
  } catch (err: any) {
    unreadCount.value = null
    notifications.value = null
    if (!notificationErrorNotified) {
      message.error(err.message || '通知数据待确认')
      notificationErrorNotified = true
    }
  }
}

function normalizeNotificationReadFlag(value: unknown): boolean | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return true
  if (value === 0 || value === '0' || value === false || value === 'false') return false
  return null
}

const connectWebSocket = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) return
  try {
    const SockJS = (window as any).SockJS
    if (!SockJS) return
    const StompJs = await import('@stomp/stompjs')
    const socket = new SockJS(new URL('/ws', API_BASE_URL).toString())
    stompClient = new StompJs.Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient?.subscribe(`/topic/notifications/${userId}`, (msg: any) => {
          try {
            const notif = normalizeNotificationItem(JSON.parse(msg.body))
            if (notifications.value === null) {
              notifications.value = []
            }
            notifications.value = [
              notif,
              ...notifications.value.filter((item: any) => Number(item.id) !== notif.id)
            ].slice(0, 20)
            if (normalizeNotificationReadFlag(notif.isRead) !== true) {
              unreadCount.value = (unreadCount.value ?? 0) + 1
            }
          } catch {
            void loadNotifications()
          }
        })
      }
    })
    stompClient.activate()
  } catch { /* WebSocket not available, fall back to polling */ }
}

function startNotificationPolling() {
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer)
  }
  notificationPollTimer = setInterval(() => {
    if (userStore.isLoggedIn && document.visibilityState === 'visible') {
      void loadNotifications()
    }
  }, 30000)
}

function stopNotificationRealtime() {
  if (notificationPollTimer) {
    clearInterval(notificationPollTimer)
    notificationPollTimer = null
  }
  if (stompClient) {
    try {
      stompClient.deactivate()
    } catch {}
    stompClient = null
  }
}

watch(() => userStore.isLoggedIn, (val) => {
  if (val) {
    void loadNotifications()
    void connectWebSocket()
    startNotificationPolling()
  } else {
    stopNotificationRealtime()
    notifications.value = null
    unreadCount.value = null
  }
})

// 监听维护通知
onMounted(() => {
  window.addEventListener('notify-maintenance', (e: Event) => {
    const msg = (e as CustomEvent).detail || '系统维护中'
    message.warning(msg, { duration: 0, closable: true })
  })
  if (userStore.isLoggedIn) {
    void loadNotifications()
    void connectWebSocket()
    startNotificationPolling()
  }
})

onUnmounted(() => {
  stopNotificationRealtime()
})

const handleMarkRead = async (n: any) => {
  if (normalizeNotificationReadFlag(n?.isRead) === true) return
  try {
    await request.post(`/api/notifications/${n.id}/read`, null, { headers: NOTIFICATION_HEADERS })
    await loadNotifications()
    const confirmed = notifications.value?.find((item: any) => Number(item.id) === Number(n.id))
    if (!confirmed || normalizeNotificationReadFlag(confirmed.isRead) !== true) {
      throw new Error('通知已读结果待确认')
    }
  } catch (err: any) {
    message.error(err.message || '通知已读失败')
  }
}

// 点击通知查看详情
const handleNotificationClick = async (n: any) => {
  await handleMarkRead(n)
  // 根据通知类型跳转
  if (n.type === 'success' || n.type === 'error') {
    // 任务完成通知 → 跳转任务中心
    if (n.relatedId) router.push({ path: '/tasks', query: { focusTask: n.relatedId } })
    else router.push('/tasks')
  } else if (n.type === 'team_invite') {
    router.push('/teams')
  } else if (n.title?.includes('公告')) {
    router.push('/dashboard')
  } else {
    // 默认跳转通知来源
    router.push('/tasks')
  }
}

const handleMarkAllRead = async () => {
  try {
    await request.post('/api/notifications/read-all', null, { headers: NOTIFICATION_HEADERS })
    await loadNotifications()
    if (unreadCount.value !== 0) {
      throw new Error('通知全部已读结果待确认')
    }
    if (notifications.value?.some((item: any) => normalizeNotificationReadFlag(item.isRead) !== true)) {
      throw new Error('通知全部已读结果待确认')
    }
  } catch (err: any) {
    message.error(err.message || '通知全部已读失败')
  }
}

// 头像下拉选择
const handleUserDropdownSelect = async (key: string) => {
  if (key === 'toggle-theme') {
    ;(window as any).__toggleTheme?.()
    return
  }
  if (key === 'logout') {
    await authApi.logout()
    message.success('您已成功退出登录')
    router.push('/login')
  } else {
    router.push(`/${key}`)
  }
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  overflow: hidden;
}

/* 磨砂玻璃侧边栏 */
.glass-sider {
  background: var(--card-color) !important;
  backdrop-filter: blur(16px);
  border-right: 1px solid var(--border-color) !important;
  box-shadow: 4px 0 20px rgba(0, 0, 0, 0.15);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  border-bottom: 1px solid var(--border-color);
}

.logo-area.collapsed {
  justify-content: center;
  padding: 24px 10px;
}

.logo-icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #10b981, #3b82f6);
  border-radius: 10px;
  box-shadow: 0 0 12px rgba(16, 185, 129, 0.4);
}

.logo-icon {
  color: #fff;
  width: 20px;
  height: 20px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(to right, #10b981, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.5px;
}

.menu-box {
  flex: 1;
  padding: 16px 0;
}



.main-layout-content {
  background-color: var(--bg-secondary) !important;
}

/* 磨砂玻璃顶部栏 */
.glass-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70px;
  padding: 0 24px;
  background: var(--card-color) !important;
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color) !important;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.proj-label {
  font-size: 13px;
  color: #9ca3af;
}

.project-selector {
  width: 180px;
}

.project-status-note {
  font-size: 12px;
  color: #f59e0b;
  white-space: nowrap;
}

.add-proj-btn {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(139, 92, 246, 0.15)) !important;
  border: 1px solid rgba(139, 92, 246, 0.3) !important;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notify-btn {
  background-color: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  color: #d1d5db !important;
}

/* 用户头像卡 */
.avatar-box {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.3s;
}

.avatar-box:hover {
  background: rgba(255, 255, 255, 0.05);
}

.user-info-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.username {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.role-badge {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 4px;
  margin-top: 2px;
  width: fit-content;
}

.role-badge.admin {
  background-color: rgba(139, 92, 246, 0.15);
  color: #a78bfa;
}

.role-badge.user {
  background-color: rgba(59, 130, 246, 0.15);
  color: #60a5fa;
}

.page-content-wrapper {
  padding: 24px;
  min-height: calc(100vh - 70px);
  background-color: var(--bg-secondary) !important;
  overflow-y: auto;
}

/* 共享按钮和弹窗 */
.share-proj-btn {
  background: rgba(16, 185, 129, 0.08) !important;
  border: 1px solid rgba(16, 185, 129, 0.2) !important;
}

.share-info-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-size: 13px;
  color: #9ca3af;
}

.share-status-note {
  margin-bottom: 12px;
  font-size: 12px;
  color: #fca5a5;
}

.share-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.share-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.04);
}

.share-team-name {
  flex: 1;
  font-size: 13px;
  color: #e5e7eb;
  font-weight: 500;
}

.add-share-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.delete-project-dialog p {
  margin: 0 0 10px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.delete-project-dialog p:last-child {
  margin-bottom: 0;
  color: #fca5a5;
}

/* ---- 通知弹窗 ---- */
.notif-popover { max-height: 400px; overflow-y: auto; }
.notif-header {
  display: flex; justify-content: space-between; align-items: center;
  padding-bottom: 8px; border-bottom: 1px solid var(--border-color);
  margin-bottom: 8px;
}
.notif-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.notif-list { display: flex; flex-direction: column; gap: 4px; }
.notif-item {
  display: flex; gap: 10px; padding: 10px; border-radius: 10px;
  cursor: pointer; transition: background 0.3s;
}
.notif-item:hover { background: rgba(255,255,255,0.04); }
.notif-unread { background: rgba(16,185,129,0.04); }
.notif-dot {
  width: 8px; height: 8px; border-radius: 50%; margin-top: 6px; flex-shrink: 0;
}
.notif-dot.success { background: #10b981; }
.notif-dot.error { background: #ef4444; }
.notif-dot.team_invite { background: #3b82f6; }
.notif-dot.info { background: #6b7280; }
.notif-body {
  flex: 1; display: flex; flex-direction: column; gap: 4px; min-width: 0;
}
.notif-item-title { font-size: 13px; font-weight: 600; color: #f3f4f6; }
.notif-content { font-size: 12px; color: #9ca3af; line-height: 1.4; }
.notif-time { font-size: 10px; color: #6b7280; }
.notif-view { font-size: 10px; color: #10b981; font-weight: 500; opacity: 0; transition: opacity .2s; }
.notif-item:hover .notif-view { opacity: 1; }
.notif-footer { display: flex; justify-content: space-between; align-items: center; margin-top: 2px; }
.notif-empty { text-align: center; padding: 30px; color: #6b7280; font-size: 13px; }
</style>
