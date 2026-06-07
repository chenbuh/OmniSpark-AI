<template>
  <div ref="dashboardRoot" class="dashboard-container">
    <div class="dashboard-aurora dashboard-aurora-left"></div>
    <div class="dashboard-aurora dashboard-aurora-right"></div>
    <!-- 系统公告 -->
    <n-alert v-if="announcement" type="info" closable class="dashboard-announcement" style="margin-bottom:16px;">
      <template #header>{{ announcement.title }}</template>
      {{ announcement.content }}
    </n-alert>
    <n-alert v-else-if="announcementLoadFailed" type="warning" closable class="dashboard-announcement" style="margin-bottom:16px;">
      <template #header>公告待确认</template>
      当前无法确认系统公告，请稍后重试。
    </n-alert>
    <n-alert v-if="metricsLoadState === 'error'" type="warning" closable class="dashboard-announcement" style="margin-bottom:16px;">
      <template #header>首页统计待确认</template>
      当前无法确认任务与资产统计，已保留页面访问，请稍后刷新。
    </n-alert>

    <div class="welcome-header">
      <div class="welcome-copy">
        <span class="welcome-kicker">OmniSpark Control Center</span>
        <h1>欢迎回来，{{ userStore.userInfo?.nickname }} 👋</h1>
        <p class="subtitle">在此统一管理真实项目资产、模型提供商与图像视频生成任务。</p>
      </div>
      <div v-if="!loading" class="hero-pills">
        <div class="hero-pill">
          <span class="hero-pill-label">活跃任务</span>
          <strong>{{ activeTasksCount }}</strong>
        </div>
        <div class="hero-pill">
          <span class="hero-pill-label">当前项目资产</span>
          <strong>{{ currentAssets.length }}</strong>
        </div>
        <div class="hero-pill">
          <span class="hero-pill-label">最近任务</span>
          <strong>{{ recentTasks.length }}</strong>
        </div>
      </div>
    </div>

    <!-- 加载骨架 -->
    <SkeletonCard v-if="loading" type="chart" />

    <!-- 数据汇总卡片 -->
    <n-row :gutter="24" class="stats-row" v-if="!loading">
      <n-col :span="8">
        <n-card class="stat-card stat-card-item glass-card purple-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">进行中的生成任务</span>
              <span class="value">{{ activeTasksCount }} <span class="unit">个</span></span>
            </div>
            <div class="icon-box purple">
              <Activity class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text">
            在当前的并发队列中，有 {{ activeTasksCount }} 个任务处于渲染排队中
          </div>
        </n-card>
      </n-col>

      <n-col :span="8">
        <n-card class="stat-card stat-card-item glass-card blue-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">当前项目资产</span>
              <span class="value">{{ currentAssets.length }} <span class="unit">个</span></span>
            </div>
            <div class="icon-box blue">
              <Library class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text">
            包含 {{ currentAssets.filter(a => a.assetType === 'image').length }} 张图片，{{ currentAssets.filter(a => a.assetType === 'video').length }} 个视频
          </div>
        </n-card>
      </n-col>

      <n-col :span="8">
        <n-card class="stat-card stat-card-item glass-card green-glow">
          <div class="card-inner">
            <div class="text-group">
              <span class="label">任务生成成功率</span>
              <span class="value">{{ successRateLabel }}</span>
            </div>
            <div class="icon-box green">
              <CheckCircle class="stat-icon" />
            </div>
          </div>
          <div class="asset-details-text success">
            {{ successRateHint }}
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 快捷通道 -->
    <n-row :gutter="24" class="quick-row">
      <n-col :span="12">
        <div class="quick-card image-channel" @click="router.push('/generate/image')">
          <div class="channel-content">
            <h3>文生图 / 图生图</h3>
            <p>使用当前项目已配置的图像模型，直接发起真实生图任务。</p>
            <div class="arrow-btn">
              <ArrowRight class="btn-icon" />
            </div>
          </div>
          <Image class="bg-icon" />
        </div>
      </n-col>
      <n-col :span="12">
        <div class="quick-card video-channel" @click="router.push('/generate/video')">
          <div class="channel-content">
            <h3>生视频中心</h3>
            <p>使用当前项目已配置的视频模型，发起文生视频或图生视频任务。</p>
            <div class="arrow-btn">
              <ArrowRight class="btn-icon" />
            </div>
          </div>
          <Video class="bg-icon" />
        </div>
      </n-col>
    </n-row>

    <n-row :gutter="24" style="margin-top: 24px;">
      <!-- 最近任务 -->
      <n-col :span="16">
        <n-card title="最近任务队列" class="glass-card panel-card tasks-panel" :bordered="false">
          <template #header-extra>
            <n-button text type="primary" @click="router.push('/tasks')">查看全部</n-button>
          </template>

          <n-table :single-line="false" class="tasks-table">
            <thead>
              <tr>
                <th>任务ID</th>
                <th>生成类型</th>
                <th>所用模型</th>
                <th>提示词描述</th>
                <th>生成状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="task in recentTasks" :key="task.id">
                <td>#{{ task.id.toString().substring(task.id.toString().length - 4) }}</td>
                <td>
                  <n-tag :type="task.taskType === 'image' ? 'success' : 'info'" size="small">
                    {{ task.taskType === 'image' ? '生图' : '视频' }}
                  </n-tag>
                </td>
                <td><code>{{ getTaskDisplayModelName(task) }}</code></td>
                <td>
                  <n-ellipsis style="max-width: 260px" :tooltip="true">
                    {{ getTaskDisplayPrompt(task) }}
                  </n-ellipsis>
                </td>
                <td>
                  <n-tag
                    :type="taskStatusTagType(task.status)"
                    size="small"
                  >
                    {{ taskStatusLabel(task.status) }}
                  </n-tag>
                </td>
                <td>
                  <n-space>
                    <n-button type="primary" size="tiny" secondary @click="handleReuse(task)">复用</n-button>
                    <n-button type="error" size="tiny" tertiary @click="handleDelete(task.id)">删除</n-button>
                  </n-space>
                </td>
              </tr>
              <tr v-if="recentTasks.length === 0">
                <td colspan="6" style="text-align: center; color: #9ca3af;">暂无任务，请前往生图或视频页创建！</td>
              </tr>
            </tbody>
          </n-table>
        </n-card>
      </n-col>

      <!-- 热门提示词 -->
      <n-col :span="8">
        <n-card title="公共热门提示词" class="glass-card panel-card templates-panel" :bordered="false">
          <div v-if="recommendedTemplates && recommendedTemplates.length > 0" class="templates-list">
            <div
              v-for="tpl in recommendedTemplates"
              :key="tpl.id"
              class="tpl-item"
              @click="handleApplyTemplate(tpl.content)"
            >
              <div class="tpl-head">
                <span class="tpl-name">{{ tpl.name }}</span>
                <n-tag size="mini" type="warning" round>{{ tpl.tag }}</n-tag>
              </div>
              <p class="tpl-content">{{ tpl.content }}</p>
            </div>
          </div>
          <n-empty v-else-if="recommendedTemplates !== null" description="公共模板库暂无热门提示词" style="padding: 20px 0;" />
          <n-empty v-else description="公共热门提示词待确认，请稍后重试。" style="padding: 20px 0;" />
        </n-card>
      </n-col>
    </n-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { gsap } from 'gsap'
import { useUserStore } from '@/store/user'
import { useProjectStore } from '@/store/project'
import { useTaskStore } from '@/store/task'
import { useAssetStore } from '@/store/asset'
import type { PromptTemplate } from '@/api/templates'
import type { GenerationTask } from '@/store/task'
import { buildGenerationReuseLocation } from '@/utils/generationReuse'
import SkeletonCard from '@/components/SkeletonCard.vue'
import request from '@/api/request'
import {
  Activity,
  Library,
  CheckCircle,
  ArrowRight,
  Image,
  Video
} from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()
const dashboardRoot = ref<HTMLElement | null>(null)

const userStore = useUserStore()
const projectStore = useProjectStore()
const taskStore = useTaskStore()
const assetStore = useAssetStore()

type DashboardAnnouncement = {
  id: number
  title: string
  content: string
  priority: string
  status: 1
}

const loading = ref(true)
const announcement = ref<DashboardAnnouncement | null>(null)
const announcementLoadFailed = ref(false)
const recommendedTemplates = ref<PromptTemplate[] | null>(null)
const metricsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
let dashboardContext: gsap.Context | null = null
let dashboardMatchMedia: gsap.MatchMedia | null = null

// 获取当前项目下的资产
const currentAssets = computed(() => {
  return assetStore.getAssetsByProject(projectStore.activeProjectId)
})

const currentProjectTasks = computed(() => {
  return taskStore.getTasksByProject(projectStore.activeProjectId)
})

function normalizeTaskField(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function tryParseTaskRequestJson(task?: { requestJson?: string } | null) {
  if (!task?.requestJson) {
    return null
  }
  try {
    const parsed = JSON.parse(task.requestJson)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null
  } catch {
    return null
  }
}

function getTaskDisplayPrompt(task?: GenerationTask | null) {
  const payload = tryParseTaskRequestJson(task)
  return normalizeTaskField(payload?.prompt) || task?.prompt || ''
}

function getTaskDisplayModelName(task?: GenerationTask | null) {
  const payload = tryParseTaskRequestJson(task)
  return normalizeTaskField(payload?.modelName) || task?.modelName || ''
}

// 获取最近的 3 个任务
const recentTasks = computed(() => {
  return [...currentProjectTasks.value]
    .sort((a, b) => Date.parse(String(b.createdAt || '').replace(' ', 'T')) - Date.parse(String(a.createdAt || '').replace(' ', 'T')))
    .slice(0, 3)
})

// 获取当前处于进行中（排队或运行）的任务数
const activeTasksCount = computed(() => {
  return currentProjectTasks.value.filter(t => t.status === 'pending' || t.status === 'running').length
})

const completedTasks = computed(() => {
  return currentProjectTasks.value.filter(t => t.status === 'success' || t.status === 'failed')
})

const successTasksCount = computed(() => {
  return completedTasks.value.filter(t => t.status === 'success').length
})

const failedTasksCount = computed(() => {
  return completedTasks.value.filter(t => t.status === 'failed').length
})

const successRateLabel = computed(() => {
  const total = completedTasks.value.length
  if (total === 0) {
    return '--'
  }
  return `${((successTasksCount.value / total) * 100).toFixed(1)}%`
})

const successRateHint = computed(() => {
  const total = completedTasks.value.length
  if (total === 0) {
    return activeTasksCount.value > 0
      ? `当前项目已有 ${activeTasksCount.value} 个任务进行中，待完成后展示真实成功率`
      : '当前项目还没有已完成任务，生成后会展示真实成功率'
  }
  if (activeTasksCount.value > 0) {
    return `已完成 ${total} 个任务，成功 ${successTasksCount.value} 个，当前还有 ${activeTasksCount.value} 个任务进行中`
  }
  return `已完成 ${total} 个任务，成功 ${successTasksCount.value} 个，失败 ${failedTasksCount.value} 个`
})

const taskStatusLabel = (status: string) => {
  if (status === 'success') return '生成成功'
  if (status === 'running') return '进行中'
  if (status === 'pending') return '排队中'
  if (status === 'failed') return '失败'
  return status || '未知'
}

const taskStatusTagType = (status: string) => {
  if (status === 'success') return 'success'
  if (status === 'running') return 'warning'
  if (status === 'failed') return 'error'
  return 'default'
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function requireNonNegativeNumber(value: unknown, errorMessage: string) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized < 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function normalizeBinaryStatus(value: unknown): number | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return 1
  if (value === 0 || value === '0' || value === false || value === 'false') return 0
  return null
}

function normalizePromptTemplateRecord(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('公共热门提示词待确认')
  }
  const record = value as Record<string, unknown>
  const id = requireNonNegativeNumber(record.id, '热门提示词待确认')
  const name = normalizeOptionalText(record.name)
  const content = normalizeOptionalText(record.content)
  if (id <= 0 || !name || !content) {
    throw new Error('公共热门提示词待确认')
  }
  return {
    id,
    userId: Number.isFinite(Number(record.userId)) ? Number(record.userId) : undefined,
    username: normalizeOptionalText(record.username) || undefined,
    nickname: normalizeOptionalText(record.nickname) || undefined,
    avatar: normalizeOptionalText(record.avatar) || undefined,
    name,
    content,
    negativePrompt: normalizeOptionalText(record.negativePrompt) || undefined,
    modelName: normalizeOptionalText(record.modelName) || undefined,
    tag: normalizeOptionalText(record.tag),
    likesCount: requireNonNegativeNumber(record.likesCount ?? 0, '热门提示词待确认'),
    commentsCount: requireNonNegativeNumber(record.commentsCount ?? 0, '热门提示词待确认'),
    liked: normalizeBinaryStatus(record.liked) ?? 0,
    status: requireNonNegativeNumber(record.status ?? 0, '热门提示词待确认'),
    createdAt: normalizeOptionalText(record.createdAt) || undefined
  }
}

function requireTemplatePage(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('公共热门提示词待确认')
  }
  const record = value as Record<string, unknown>
  const records = record.records
  const total = record.total
  if (!Array.isArray(records)) {
    throw new Error('公共热门提示词待确认')
  }
  const normalizedRecords = records.map(item => normalizePromptTemplateRecord(item))
  const ids = new Set<number>()
  normalizedRecords.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('热门提示词待确认')
    }
    ids.add(item.id)
  })
  const normalizedTotal = requireNonNegativeNumber(total, '热门提示词待确认')
  if (normalizedRecords.length > normalizedTotal) {
    throw new Error('公共热门提示词待确认')
  }
  return {
    records: normalizedRecords,
    total: normalizedTotal
  }
}

function normalizeAnnouncementRecord(value: unknown): DashboardAnnouncement {
  if (!isPlainObject(value)) {
    throw new Error('公告待确认')
  }
  const id = requireNonNegativeNumber(value.id, '公告待确认')
  const title = normalizeOptionalText(value.title)
  const content = normalizeOptionalText(value.content)
  const priority = normalizeOptionalText(value.priority)
  const status = normalizeBinaryStatus(value.status)
  if (id <= 0 || !title || !content || !priority || status !== 1) {
    throw new Error('公告待确认')
  }
  return {
    id,
    title,
    content,
    priority,
    status
  }
}

function requireAnnouncementList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('公告待确认')
  }
  const seenIds = new Set<number>()
  return value.map((item: unknown) => {
    const normalized = normalizeAnnouncementRecord(item)
    if (seenIds.has(normalized.id)) {
      throw new Error('公告待确认')
    }
    seenIds.add(normalized.id)
    return normalized
  })
}

async function loadDashboardData(options?: { loading?: boolean }) {
  if (options?.loading) {
    loading.value = true
  }
  const activeProjectId = projectStore.activeProjectId
  const [tasksResult, assetsResult, tplResult, annResult] = await Promise.allSettled([
    activeProjectId ? taskStore.refresh({ projectId: activeProjectId }) : Promise.resolve(),
    activeProjectId ? assetStore.refresh({ projectId: activeProjectId }) : Promise.resolve(),
    request.get('/api/prompt-templates', {
      params: { sort: 'likes', page: 1, pageSize: 3 },
      headers: NO_CACHE_HEADERS
    }),
    request.get('/api/announcements/active', { headers: NO_CACHE_HEADERS })
  ])

  metricsLoadState.value = !activeProjectId || (tasksResult.status === 'fulfilled' && assetsResult.status === 'fulfilled')
    ? 'ready'
    : 'error'

  if (tplResult.status === 'fulfilled') {
    try {
      const data = requireTemplatePage(getResponseData(tplResult.value, '热门提示词待确认'))
      recommendedTemplates.value = data.records
    } catch (err) {
      recommendedTemplates.value = null
      console.error(err)
    }
  } else {
    recommendedTemplates.value = null
    console.error(tplResult.reason)
  }

  if (annResult.status === 'fulfilled') {
    try {
      const normalized = requireAnnouncementList(getResponseData(annResult.value, '公告待确认'))
      announcement.value = normalized.length > 0 ? normalized[0] : null
      announcementLoadFailed.value = false
    } catch {
      announcement.value = null
      announcementLoadFailed.value = true
    }
  } else {
    announcement.value = null
    announcementLoadFailed.value = true
    console.error(annResult.reason)
  }

  loading.value = false
}

const shouldReduceMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

const animateMetricRefresh = async () => {
  await nextTick()
  if (!dashboardRoot.value || shouldReduceMotion()) return
  const targets = Array.from(dashboardRoot.value.querySelectorAll<HTMLElement>('.hero-pill strong, .stat-card .value'))
  if (targets.length === 0) return

  gsap.killTweensOf(targets)
  gsap.fromTo(
    targets,
    { y: 8, autoAlpha: 0.65 },
    {
      y: 0,
      autoAlpha: 1,
      duration: 0.34,
      stagger: 0.04,
      ease: 'power2.out',
      clearProps: 'transform,opacity,visibility'
    }
  )
}

const animateTemplateRefresh = async () => {
  await nextTick()
  if (!dashboardRoot.value || shouldReduceMotion()) return
  const items = Array.from(dashboardRoot.value.querySelectorAll<HTMLElement>('.tpl-item'))
  if (items.length === 0) return

  gsap.killTweensOf(items)
  gsap.fromTo(
    items,
    { y: 12, autoAlpha: 0 },
    {
      y: 0,
      autoAlpha: 1,
      duration: 0.32,
      stagger: 0.05,
      ease: 'power2.out',
      clearProps: 'transform,opacity,visibility'
    }
  )
}

const setupDashboardMotion = async () => {
  await nextTick()
  const rootEl = dashboardRoot.value
  if (!rootEl) return

  dashboardContext?.revert()
  dashboardMatchMedia?.revert()

  dashboardContext = gsap.context(() => {
    dashboardMatchMedia = gsap.matchMedia()
    dashboardMatchMedia.add(
      {
        isReduced: '(prefers-reduced-motion: reduce)',
        isMobile: '(max-width: 768px)'
      },
      (context) => {
        const { isReduced, isMobile } = context.conditions as { isReduced: boolean; isMobile: boolean }
        const root = dashboardRoot.value
        if (!root) return

        const announcementEl = root.querySelector('.dashboard-announcement')
        const kicker = root.querySelector('.welcome-kicker')
        const title = root.querySelector('.welcome-header h1')
        const subtitle = root.querySelector('.welcome-header .subtitle')
        const pills = Array.from(root.querySelectorAll<HTMLElement>('.hero-pill'))
        const statCards = Array.from(root.querySelectorAll<HTMLElement>('.stat-card'))
        const quickCards = Array.from(root.querySelectorAll<HTMLElement>('.quick-card'))
        const panels = Array.from(root.querySelectorAll<HTMLElement>('.panel-card'))
        const tableRows = Array.from(root.querySelectorAll<HTMLElement>('.tasks-table tbody tr'))
        const templateItems = Array.from(root.querySelectorAll<HTMLElement>('.tpl-item'))
        const auroras = Array.from(root.querySelectorAll<HTMLElement>('.dashboard-aurora'))
        const allTargets = [
          ...pills,
          ...statCards,
          ...quickCards,
          ...panels,
          ...tableRows,
          ...templateItems,
          ...auroras,
          announcementEl,
          kicker,
          title,
          subtitle
        ].filter(Boolean) as HTMLElement[]

        if (isReduced) {
          gsap.set(allTargets, { clearProps: 'transform,opacity,visibility,filter' })
          return
        }

        const timeline = gsap.timeline({ defaults: { ease: 'power3.out' } })
        if (announcementEl) {
          timeline.fromTo(
            announcementEl,
            { autoAlpha: 0, y: -18 },
            { autoAlpha: 1, y: 0, duration: 0.4, clearProps: 'transform,opacity,visibility' }
          )
        }
        if (kicker) {
          timeline.fromTo(
            kicker,
            { autoAlpha: 0, x: -18 },
            { autoAlpha: 1, x: 0, duration: 0.34, clearProps: 'transform,opacity,visibility' },
            announcementEl ? '-=0.2' : 0
          )
        }
        if (title) {
          timeline.fromTo(
            title,
            { autoAlpha: 0, y: 22, filter: 'blur(8px)' },
            { autoAlpha: 1, y: 0, filter: 'blur(0px)', duration: 0.62, clearProps: 'transform,opacity,visibility,filter' },
            announcementEl ? '-=0.08' : 0.06
          )
        }
        if (subtitle) {
          timeline.fromTo(
            subtitle,
            { autoAlpha: 0, y: 16 },
            { autoAlpha: 1, y: 0, duration: 0.34, clearProps: 'transform,opacity,visibility' },
            '-=0.38'
          )
        }
        if (pills.length > 0) {
          timeline.fromTo(
            pills,
            { autoAlpha: 0, y: 16, scale: 0.96 },
            {
              autoAlpha: 1,
              y: 0,
              scale: 1,
              duration: 0.34,
              stagger: 0.06,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.18'
          )
        }
        if (statCards.length > 0) {
          timeline.fromTo(
            statCards,
            { autoAlpha: 0, y: 24 },
            {
              autoAlpha: 1,
              y: 0,
              duration: 0.46,
              stagger: isMobile ? 0.08 : 0.06,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.14'
          )
        }
        if (quickCards.length > 0) {
          timeline.fromTo(
            quickCards,
            { autoAlpha: 0, y: 24, scale: 0.98 },
            {
              autoAlpha: 1,
              y: 0,
              scale: 1,
              duration: 0.44,
              stagger: 0.08,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.16'
          )
        }
        if (panels.length > 0) {
          timeline.fromTo(
            panels,
            { autoAlpha: 0, y: 22 },
            {
              autoAlpha: 1,
              y: 0,
              duration: 0.42,
              stagger: 0.08,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.12'
          )
        }
        if (tableRows.length > 0) {
          timeline.fromTo(
            tableRows,
            { autoAlpha: 0, x: -14 },
            {
              autoAlpha: 1,
              x: 0,
              duration: 0.28,
              stagger: 0.04,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.16'
          )
        }
        if (templateItems.length > 0) {
          timeline.fromTo(
            templateItems,
            { autoAlpha: 0, y: 16 },
            {
              autoAlpha: 1,
              y: 0,
              duration: 0.28,
              stagger: 0.05,
              clearProps: 'transform,opacity,visibility'
            },
            '-=0.18'
          )
        }

        const loopTweens: gsap.core.Tween[] = []
        const leftAurora = root.querySelector('.dashboard-aurora-left')
        const rightAurora = root.querySelector('.dashboard-aurora-right')
        if (leftAurora) {
          loopTweens.push(
            gsap.to(leftAurora, {
              x: 28,
              y: -18,
              duration: 7,
              repeat: -1,
              yoyo: true,
              ease: 'sine.inOut'
            })
          )
        }
        if (rightAurora) {
          loopTweens.push(
            gsap.to(rightAurora, {
              x: -24,
              y: 18,
              duration: 8,
              repeat: -1,
              yoyo: true,
              ease: 'sine.inOut'
            })
          )
        }

        const cleanupCallbacks: Array<() => void> = []
        quickCards.forEach((card) => {
          const icon = card.querySelector('.bg-icon') as HTMLElement | null
          const arrow = card.querySelector('.arrow-btn') as HTMLElement | null
          const onEnter = () => {
            gsap.to(card, { y: -6, scale: 1.012, duration: 0.35, ease: 'power2.out', overwrite: 'auto' })
            if (icon) {
              gsap.to(icon, { x: -10, y: -6, rotation: -6, duration: 0.45, ease: 'power2.out', overwrite: 'auto' })
            }
            if (arrow) {
              gsap.to(arrow, { x: 6, duration: 0.3, ease: 'power2.out', overwrite: 'auto' })
            }
          }
          const onLeave = () => {
            gsap.to(card, { y: 0, scale: 1, duration: 0.32, ease: 'power2.out', overwrite: 'auto' })
            if (icon) {
              gsap.to(icon, { x: 0, y: 0, rotation: 0, duration: 0.42, ease: 'power2.out', overwrite: 'auto' })
            }
            if (arrow) {
              gsap.to(arrow, { x: 0, duration: 0.26, ease: 'power2.out', overwrite: 'auto' })
            }
          }
          card.addEventListener('mouseenter', onEnter)
          card.addEventListener('mouseleave', onLeave)
          cleanupCallbacks.push(() => {
            card.removeEventListener('mouseenter', onEnter)
            card.removeEventListener('mouseleave', onLeave)
          })
        })

        return () => {
          loopTweens.forEach((tween) => tween.kill())
          cleanupCallbacks.forEach((cleanup) => cleanup())
        }
      },
      rootEl
    )
  }, rootEl)
}

onMounted(async () => {
  try {
    await loadDashboardData({ loading: true })
  } finally {
    void setupDashboardMotion()
  }
})

watch(() => projectStore.activeProjectId, async () => {
  try {
    await loadDashboardData()
  } catch (e) {
    console.error(e)
  }
  void animateMetricRefresh()
  void animateTemplateRefresh()
})

watch(() => loading.value, (value) => {
  if (!value) {
    void setupDashboardMotion()
  }
})

watch(
  () => [activeTasksCount.value, currentAssets.value.length, recentTasks.value.length].join(':'),
  () => {
    void animateMetricRefresh()
  }
)

watch(
  () => (recommendedTemplates.value || []).map(item => item.id).join(':'),
  () => {
    void animateTemplateRefresh()
  }
)

// 复用提示词
const handleReuse = (task: GenerationTask) => {
  router.push(buildGenerationReuseLocation(task))
  const payload = tryParseTaskRequestJson(task)
  message.success(
    payload
      ? '已将该任务的真实生成参数带入对应面板'
      : '已将该任务已记录的提示词、模型与基础参数带入对应面板'
  )
}

// 删除任务
const handleDelete = async (id: number) => {
  try {
    const previousCount = currentProjectTasks.value.length
    await taskStore.deleteTask(id)
    if (currentProjectTasks.value.some(task => task.id === id) || currentProjectTasks.value.length !== Math.max(0, previousCount - 1)) {
      throw new Error('任务删除结果待确认')
    }
    message.success('任务删除成功')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '任务删除失败'))
  }
}

// 应用提示词模板
const handleApplyTemplate = (content: string) => {
  router.push({
    path: '/generate/image',
    query: { prompt: content }
  })
  message.success('公共模板提示词已导入生图面板')
}

onBeforeUnmount(() => {
  dashboardMatchMedia?.revert()
  dashboardContext?.revert()
})
</script>

<style scoped>
.dashboard-container {
  position: relative;
  padding-bottom: 40px;
  overflow: hidden;
  isolation: isolate;
}

.dashboard-container::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.08), transparent 28%),
    radial-gradient(circle at 85% 12%, rgba(59, 130, 246, 0.08), transparent 26%);
  pointer-events: none;
}

.dashboard-container > * {
  position: relative;
  z-index: 1;
}

.dashboard-aurora {
  position: absolute;
  width: 280px;
  height: 280px;
  border-radius: 999px;
  filter: blur(40px);
  opacity: 0.38;
  pointer-events: none;
  z-index: 0;
}

.dashboard-aurora-left {
  top: -120px;
  left: -100px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.34) 0%, rgba(16, 185, 129, 0.08) 44%, transparent 72%);
}

.dashboard-aurora-right {
  top: 32px;
  right: -140px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.3) 0%, rgba(59, 130, 246, 0.08) 42%, transparent 72%);
}

.welcome-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin-bottom: 28px;
}

.welcome-copy {
  max-width: 640px;
}

.welcome-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  margin-bottom: 14px;
  border: 1px solid rgba(16, 185, 129, 0.18);
  border-radius: 999px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.12), rgba(59, 130, 246, 0.08));
  color: #9ae6b4;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.welcome-header h1 {
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 800;
  margin: 0 0 6px 0;
  color: #fff;
  line-height: 1.06;
  letter-spacing: -0.03em;
  text-wrap: balance;
}

.subtitle {
  max-width: 580px;
  font-size: 15px;
  color: #94a3b8;
  margin: 0;
  line-height: 1.7;
}

.hero-pills {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
}

.hero-pill {
  min-width: 116px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.78), rgba(15, 23, 42, 0.52));
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.2);
  backdrop-filter: blur(16px);
}

.hero-pill-label {
  display: block;
  margin-bottom: 8px;
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-pill strong {
  font-size: 24px;
  font-weight: 800;
  color: #f8fafc;
}

/* 磨砂玻璃卡片 */
.glass-card {
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.82) 0%, rgba(15, 23, 42, 0.66) 100%),
    rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.24);
}

.stat-card {
  position: relative;
  overflow: hidden;
  padding: 10px 0;
  transition: all 0.3s ease;
}

.stat-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 0%, rgba(255, 255, 255, 0.06) 48%, transparent 100%);
  transform: translateX(-100%);
  transition: transform 0.6s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
}

.stat-card:hover::after {
  transform: translateX(100%);
}

/* 霓虹发光阴影效果 */
.purple-glow:hover { box-shadow: 0 0 20px rgba(139, 92, 246, 0.15); }
.blue-glow:hover { box-shadow: 0 0 20px rgba(59, 130, 246, 0.15); }
.green-glow:hover { box-shadow: 0 0 20px rgba(16, 185, 129, 0.15); }

.card-inner {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.text-group {
  display: flex;
  flex-direction: column;
}

.label {
  font-size: 13px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.value {
  font-size: 32px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.unit {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-muted);
}

.icon-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 12px;
}

.icon-box.purple { background-color: rgba(139, 92, 246, 0.15); color: #a78bfa; }
.icon-box.blue { background-color: rgba(59, 130, 246, 0.15); color: #60a5fa; }
.icon-box.green { background-color: rgba(16, 185, 129, 0.15); color: #34d399; }

.stat-icon {
  width: 22px;
  height: 22px;
}

.stat-progress {
  margin-top: 20px;
}

.asset-details-text {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 18px;
}

.asset-details-text.success {
  color: #34d399;
}

/* 快捷通道设计 */
.quick-row {
  margin-top: 24px;
}

.quick-card {
  position: relative;
  height: 120px;
  padding: 24px;
  border-radius: 16px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  isolation: isolate;
}

.image-channel {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.14) 0%, rgba(59, 130, 246, 0.06) 100%);
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.image-channel:hover {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(59, 130, 246, 0.08) 100%);
  border-color: rgba(16, 185, 129, 0.4);
  box-shadow: 0 8px 30px rgba(16, 185, 129, 0.15);
  transform: scale(1.01);
}

.video-channel {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.14) 0%, rgba(14, 165, 233, 0.06) 100%);
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.video-channel:hover {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15) 0%, rgba(139, 92, 246, 0.08) 100%);
  border-color: rgba(59, 130, 246, 0.4);
  box-shadow: 0 8px 30px rgba(59, 130, 246, 0.15);
  transform: scale(1.01);
}

.channel-content {
  z-index: 2;
}

.channel-content h3 {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: #fff;
}

.channel-content p {
  font-size: 13px;
  color: #9ca3af;
  margin: 0 0 16px 0;
}

.arrow-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  transition: all 0.3s;
}

.quick-card:hover .arrow-btn {
  background: #fff;
  color: #000;
  transform: translateX(4px);
}

.btn-icon {
  width: 14px;
  height: 14px;
}

.bg-icon {
  position: absolute;
  right: 20px;
  bottom: -15px;
  width: 100px;
  height: 100px;
  opacity: 0.08;
  color: #fff;
  z-index: 1;
}

.panel-card {
  min-height: 100%;
}

/* 表格定制 */
.tasks-table {
  background-color: transparent !important;
}

.tasks-table th {
  background-color: rgba(128, 128, 128, 0.02) !important;
  color: var(--text-muted) !important;
  border-bottom: 1px solid var(--border-color) !important;
}

.tasks-table td {
  border-bottom: 1px solid rgba(255, 255, 255, 0.04) !important;
  color: #e5e7eb;
}

/* 推荐提示词项 */
.templates-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tpl-item {
  padding: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0.02));
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s;
}

.tpl-item:hover {
  background: rgba(128, 128, 128, 0.05);
  border-color: rgba(128, 128, 128, 0.2);
  transform: translateX(2px);
}

.tpl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.tpl-name {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.tpl-content {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (max-width: 900px) {
  .welcome-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-pills {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .hero-pill {
    min-width: 104px;
  }

  .dashboard-aurora {
    width: 190px;
    height: 190px;
    opacity: 0.3;
  }
}
</style>
