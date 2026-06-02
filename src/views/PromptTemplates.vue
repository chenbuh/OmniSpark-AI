<template>
  <div class="templates-container">
    <div class="page-header">
      <h2>提示词模板库 (Prompt Templates)</h2>
      <p class="subtitle">管理并收藏优秀的 AI 提示词指令。点击模板卡片一键应用至生图或视频面板。</p>
    </div>

    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-tabs v-model:value="activeTag" type="segment" class="filter-tabs">
          <n-tab name="all">全部类型</n-tab>
          <n-tab name="写实/人像">写实/人像</n-tab>
          <n-tab name="动漫">二次元动漫</n-tab>
          <n-tab name="赛博朋克">赛博朋克</n-tab>
          <n-tab name="科幻">宇宙科幻</n-tab>
          <n-tab name="3D渲染">3D 渲染</n-tab>
        </n-tabs>
        <n-space>
          <n-select v-model:value="sortBy" :options="sortOptions" style="width: 136px;" size="small" />
          <n-input v-model:value="searchQuery" placeholder="搜索模板..." style="width: 180px;" clearable>
            <template #prefix><Search class="s-icon" /></template>
          </n-input>
          <n-button type="primary" size="medium" @click="showAddModal = true; editingId = null; resetForm()">
            <template #icon><Plus /></template>新建模板
          </n-button>
        </n-space>
      </div>
    </n-card>

    <div class="templates-grid" v-if="templates.length > 0">
      <div v-for="tpl in templates" :key="tpl.id" class="tpl-card glass-card">
        <div class="tpl-header">
          <span class="tpl-name">{{ tpl.name }}</span>
          <n-tag type="warning" size="mini" round>{{ tpl.tag }}</n-tag>
        </div>
        <p class="tpl-content">{{ tpl.content }}</p>
        <div class="tpl-author">
          <n-avatar round size="small" :src="tpl.avatar || undefined">
            {{ authorInitial(tpl) }}
          </n-avatar>
          <div class="tpl-author-meta">
            <span class="tpl-author-name">{{ authorName(tpl) }}</span>
            <span class="tpl-author-time">{{ formatTime(tpl.createdAt) }}</span>
          </div>
        </div>
        <div class="tpl-meta">
          <n-space :size="8">
            <code v-if="tpl.modelName">{{ tpl.modelName }}</code>
            <span class="tpl-stat"><ThumbsUp class="tiny-icon" /> {{ tpl.likesCount || 0 }}</span>
            <span class="tpl-stat"><MessageCircle class="tiny-icon" /> {{ tpl.commentsCount || 0 }}</span>
          </n-space>
        </div>
        <div class="card-footer" @click.stop>
          <n-space :size="6" wrap>
            <n-button size="tiny" type="primary" secondary @click="handleApplyTemplate(tpl)">
              <template #icon><Zap /></template>生图
            </n-button>
            <n-button size="tiny" type="warning" secondary @click="handleApplyToVideo(tpl)">
              <template #icon><Video /></template>视频
            </n-button>
            <n-button size="tiny" quaternary @click="handleLike(tpl)" :type="tpl.liked ? 'primary' : 'default'">
              <template #icon><ThumbsUp /></template>{{ tpl.likesCount || 0 }}
            </n-button>
            <n-button size="tiny" quaternary @click="openComments(tpl)">
              <template #icon><MessageCircle /></template>{{ tpl.commentsCount || 0 }}
            </n-button>
          </n-space>
          <n-space :size="4" v-if="canManage(tpl)">
            <n-button size="tiny" quaternary @click="handleCopyPrompt(tpl)">
              <template #icon><Copy /></template>
            </n-button>
            <n-button size="tiny" quaternary @click="handleEdit(tpl)">
              <template #icon><Edit3 /></template>
            </n-button>
            <n-button size="tiny" type="error" tertiary @click="handleDelete(tpl.id)">
              <template #icon><Trash2 /></template>
            </n-button>
          </n-space>
        </div>
      </div>
    </div>

    <n-empty v-else description="暂无匹配的模板，点击「新建模板」创建第一个！" style="padding: 80px 0;">
      <template #extra>
        <BookOpen style="width:48px;height:48px;opacity:0.3;margin-bottom:8px;" />
      </template>
    </n-empty>

    <div class="pager" v-if="totalTemplates > 0">
      <n-pagination
        v-model:page="page"
        :page-size="pageSize"
        :item-count="totalTemplates"
        show-size-picker
        :page-sizes="pageSizeOptions"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <n-modal v-model:show="showAddModal" preset="card" :title="editingId ? '编辑提示词模板' : '新建提示词模板'" style="width: 560px;" closable>
      <n-form :model="form" label-placement="top" style="margin-top: 10px;">
        <n-form-item label="模板名称" required>
          <n-input v-model:value="form.name" placeholder="例如: 极致写实赛车, 日系新海诚画风" :maxlength="80" show-count />
        </n-form-item>
        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="分类标签">
              <n-select v-model:value="form.tag" filterable :options="tagOptions" placeholder="请选择或输入标签" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="模型名称">
              <n-input v-model:value="form.modelName" placeholder="如 dall-e-3" />
            </n-form-item>
          </n-col>
        </n-row>
        <n-form-item label="核心提示词 (Prompt)" required>
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="完整的英文 Prompt 描述内容..." :maxlength="2000" show-count />
        </n-form-item>
        <n-form-item label="负向提示词 (Negative Prompt)">
          <n-input v-model:value="form.negativePrompt" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" placeholder="可选，填写不想出现在画面中的元素" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="handleSave" :loading="saving">{{ editingId ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="showCommentDrawer" :width="460" placement="right">
      <n-drawer-content :title="selectedTemplate?.name || '模板互动'" closable>
        <div v-if="selectedTemplate" class="thread-header">
          <div class="thread-author">
            <n-avatar round size="small" :src="selectedTemplate.avatar || undefined">
              {{ authorInitial(selectedTemplate) }}
            </n-avatar>
            <span>{{ authorName(selectedTemplate) }}</span>
          </div>
          <p class="thread-content">{{ selectedTemplate.content }}</p>
        </div>
        <PublicCommentThread
          v-if="selectedTemplate"
          resource-path="/api/prompt-templates"
          :resource-id="selectedTemplate.id"
          @count-change="handleCommentCountChange"
        />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import request from '@/api/request'
import PublicCommentThread from '@/components/PublicCommentThread.vue'
import { templateApi, type PromptTemplate } from '@/api/templates'
import { Plus, Trash2, BookOpen, Search, Edit3, Zap, Video, Copy, ThumbsUp, MessageCircle } from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const projectStore = useProjectStore()

const activeTag = ref('all')
const sortBy = ref('newest')
const searchQuery = ref('')
const showAddModal = ref(false)
const showCommentDrawer = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const templates = ref<PromptTemplate[]>([])
const selectedTemplate = ref<PromptTemplate | null>(null)
const currentUserId = ref<number | null>(null)
const totalTemplates = ref(0)
let searchTimer: ReturnType<typeof setTimeout> | null = null
const page = ref(1)
const pageSize = ref(12)
const pageSizeOptions = [12, 24, 48, 96]

const form = reactive({
  name: '', tag: '写实/人像', content: '', negativePrompt: '', modelName: ''
})

const tagOptions = [
  { label: '写实/人像', value: '写实/人像' }, { label: '二次元动漫', value: '动漫' },
  { label: '赛博朋克', value: '赛博朋克' }, { label: '科幻', value: '科幻' },
  { label: '3D渲染', value: '3D渲染' }, { label: '奇幻', value: '奇幻' },
  { label: '插画', value: '插画' }, { label: '建筑设计', value: '建筑设计' }
]
const sortOptions = [
  { label: '最新发布', value: 'newest' },
  { label: '最多点赞', value: 'likes' },
  { label: '最多评论', value: 'comments' }
]

async function loadTemplates() {
  try {
    const res = await templateApi.getTemplates({
      projectId: projectStore.activeProjectId,
      tag: activeTag.value !== 'all' ? activeTag.value : undefined,
      search: searchQuery.value.trim() || undefined,
      sort: sortBy.value,
      page: page.value,
      pageSize: pageSize.value
    })
    templates.value = res.data?.records || []
    totalTemplates.value = Number(res.data?.total || 0)
  } catch {
    templates.value = []
    totalTemplates.value = 0
  }
}

function scheduleLoadTemplates(delay = 180) {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadTemplates()
  }, delay)
}

onMounted(() => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    currentUserId.value = info.id || null
  } catch {}
  loadTemplates()
})
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
watch([activeTag, sortBy, searchQuery, () => projectStore.activeProjectId], () => {
  page.value = 1
  scheduleLoadTemplates()
})
watch([page, pageSize], () => {
  scheduleLoadTemplates(0)
})

const resetForm = () => {
  Object.assign(form, { name: '', tag: '写实/人像', content: '', negativePrompt: '', modelName: '' })
  editingId.value = null
}
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}

function authorName(tpl: PromptTemplate) {
  return tpl.nickname || tpl.username || '匿名用户'
}

function authorInitial(tpl: PromptTemplate) {
  return authorName(tpl).slice(0, 1).toUpperCase()
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function canManage(tpl: PromptTemplate) {
  return !!tpl.userId && !!currentUserId.value && tpl.userId === currentUserId.value
}

function handleApplyTemplate(tpl: PromptTemplate) {
  const query: Record<string, string> = { prompt: tpl.content }
  if (tpl.negativePrompt) query.negPrompt = tpl.negativePrompt
  if (tpl.modelName) query.model = tpl.modelName
  router.push({ path: '/generate/image', query })
  message.success(`已应用「${tpl.name}」到生图面板`)
}

function handleApplyToVideo(tpl: PromptTemplate) {
  const query: Record<string, string> = { prompt: tpl.content }
  if (tpl.negativePrompt) query.negPrompt = tpl.negativePrompt
  if (tpl.modelName) query.model = tpl.modelName
  router.push({ path: '/generate/video', query })
  message.success(`已应用「${tpl.name}」到视频面板`)
}

async function handleLike(tpl: PromptTemplate) {
  try {
    const res = await request.post(`/api/prompt-templates/${tpl.id}/like`)
    const liked = (res as any).data
    tpl.liked = liked
    tpl.likesCount = Math.max(0, (tpl.likesCount || 0) + (liked ? 1 : -1))
  } catch (err: any) {
    message.error(err.message || '点赞失败')
  }
}

function openComments(tpl: PromptTemplate) {
  selectedTemplate.value = tpl
  showCommentDrawer.value = true
}

function handleCommentCountChange(count: number) {
  if (!selectedTemplate.value) return
  selectedTemplate.value.commentsCount = count
  const target = templates.value.find(item => item.id === selectedTemplate.value?.id)
  if (target) {
    target.commentsCount = count
  }
}

async function handleCopyPrompt(tpl: PromptTemplate) {
  try {
    const text = `Prompt: ${tpl.content}${tpl.negativePrompt ? `\nNegative: ${tpl.negativePrompt}` : ''}${tpl.modelName ? `\nModel: ${tpl.modelName}` : ''}`
    await navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

const handleEdit = (tpl: PromptTemplate) => {
  editingId.value = tpl.id
  form.name = tpl.name
  form.tag = tpl.tag || '写实/人像'
  form.content = tpl.content
  form.negativePrompt = tpl.negativePrompt || ''
  form.modelName = tpl.modelName || ''
  showAddModal.value = true
}

const handleSave = async () => {
  if (!form.name || !form.content) {
    message.error('请填写完整名称与指令内容！')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await templateApi.update(editingId.value, {
        projectId: projectStore.activeProjectId,
        name: form.name,
        tag: form.tag,
        content: form.content,
        negativePrompt: form.negativePrompt || undefined,
        modelName: form.modelName || undefined
      })
      message.success('模板已更新！')
    } else {
      await templateApi.createTemplate({
        projectId: projectStore.activeProjectId,
        name: form.name,
        tag: form.tag,
        content: form.content,
        negativePrompt: form.negativePrompt || undefined,
        modelName: form.modelName || undefined
      })
      message.success('新提示词模板已收录！')
    }
    await loadTemplates()
    resetForm()
    showAddModal.value = false
  } catch (err: any) {
    message.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (id: number) => {
  dialog.warning({
    title: '确认删除',
    content: '删除后无法恢复，确定要删除该模板吗？',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await templateApi.deleteTemplate(id)
        await loadTemplates()
        message.success('已删除')
      } catch (err: any) {
        message.error(err.message || '删除失败')
      }
    }
  })
}
</script>

<style scoped>
.templates-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.filter-row { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.filter-tabs { max-width: 700px; }
.s-icon { width: 14px; height: 14px; color: var(--text-muted); }

.templates-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; margin-top: 24px; }
.pager { display: flex; justify-content: flex-end; margin-top: 20px; }
.tpl-card { display: flex; flex-direction: column; padding: 16px; transition: all .25s; }
.tpl-card:hover { transform: translateY(-3px); border-color: #10b981 !important; box-shadow: 0 8px 24px rgba(0,0,0,0.25); }
.tpl-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; gap: 8px; }
.tpl-name { font-size: 14px; font-weight: 600; color: var(--text-primary); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tpl-content { font-size: 12px; color: var(--text-muted); line-height: 1.5; flex: 1; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; margin: 0; }
.tpl-author { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.tpl-author-meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.tpl-author-name { font-size: 12px; color: var(--text-secondary); font-weight: 600; }
.tpl-author-time { font-size: 11px; color: var(--text-muted); }
.tpl-meta { margin-top: 8px; }
.tpl-meta code { font-size: 10px; color: var(--text-muted); }
.tpl-stat { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-muted); }
.tiny-icon { width: 12px; height: 12px; }
.card-footer { margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--border-light); display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.thread-header { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.thread-author { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-secondary); }
.thread-content { margin: 0; padding: 12px; border-radius: 12px; background: rgba(255,255,255,0.04); color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
</style>
