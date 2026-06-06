<template>
  <div class="templates-container">
    <div class="page-header">
      <h2>提示词模板库 (Prompt Templates)</h2>
      <p class="subtitle">公共提示词模板库，管理并收藏优秀的 AI 提示词指令，跨项目一键应用至生图或视频面板。</p>
    </div>

    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-tabs v-model:value="activeTag" type="segment" class="filter-tabs">
          <n-tab name="all">全部类型</n-tab>
          <n-tab v-for="tag in templateTags" :key="tag" :name="tag">{{ tag }}</n-tab>
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
      <div v-if="tagLoadState === 'error'" class="filter-status">模板标签待确认，请稍后重试。</div>
    </n-card>

    <div v-if="loadingTemplates && templates === null" class="loading-box">
      <n-spin size="small" />
    </div>

    <div class="templates-grid" v-else-if="templates && templates.length > 0">
      <div v-for="tpl in templates" :key="tpl.id" class="tpl-card glass-card">
        <div class="tpl-header">
          <span class="tpl-name">{{ tpl.name }}</span>
          <n-tag v-if="tpl.tag" type="warning" size="mini" round>{{ tpl.tag }}</n-tag>
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
            <span class="tpl-stat"><ThumbsUp class="tiny-icon" /> {{ formatInteractionCount(tpl.likesCount) }}</span>
            <span class="tpl-stat"><MessageCircle class="tiny-icon" /> {{ formatInteractionCount(tpl.commentsCount) }}</span>
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
              <template #icon><ThumbsUp /></template>{{ formatInteractionCount(tpl.likesCount) }}
            </n-button>
            <n-button size="tiny" quaternary @click="openComments(tpl)">
              <template #icon><MessageCircle /></template>{{ formatInteractionCount(tpl.commentsCount) }}
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

    <n-empty
      v-else-if="templates !== null"
      description="暂无匹配的模板，点击「新建模板」创建第一个！"
      style="padding: 80px 0;"
    >
      <template #extra>
        <BookOpen style="width:48px;height:48px;opacity:0.3;margin-bottom:8px;" />
      </template>
    </n-empty>
    <n-empty v-else description="模板数据待确认，请稍后重试。" style="padding: 80px 0;" />

    <div class="pager" v-if="(totalTemplates ?? 0) > 0">
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
              <n-select
                v-model:value="form.tag"
                filterable
                tag
                :options="tagOptions"
                placeholder="请选择或输入标签"
              />
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
import { computed, ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
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
const loadingTemplates = ref(false)
const templates = ref<PromptTemplate[] | null>(null)
const templateTags = ref<string[]>([])
const tagLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const selectedTemplate = ref<PromptTemplate | null>(null)
const currentUserId = ref<number | null>(null)
const totalTemplates = ref<number | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null
const page = ref(1)
const pageSize = ref(12)
const pageSizeOptions = [12, 24, 48, 96]
const PUBLIC_TEMPLATE_LIBRARY_PROJECT_ID = 0

const form = reactive({
  name: '', tag: '', content: '', negativePrompt: '', modelName: ''
})

const tagOptions = computed(() => {
  const values = new Set<string>(templateTags.value)
  if (form.tag?.trim()) {
    values.add(form.tag.trim())
  }
  return Array.from(values).map(item => ({ label: item, value: item }))
})
const sortOptions = [
  { label: '最新发布', value: 'newest' },
  { label: '最多点赞', value: 'likes' },
  { label: '最多评论', value: 'comments' }
]

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

function requireStringList(value: unknown, errorMessage: string) {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const normalized: string[] = []
  const seen = new Set<string>()
  value.forEach((item: unknown) => {
    const text = typeof item === 'string' ? item.trim() : ''
    if (!text || seen.has(text)) {
      throw new Error(errorMessage)
    }
    seen.add(text)
    normalized.push(text)
  })
  return normalized
}

function requireTemplatePage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('模板数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('模板数据待确认')
  }
  const seenIds = new Set<number>()
  const normalizedRecords = records.map((item: unknown) => {
    const normalized = requireTemplateDetail(item, 'update')
    if (seenIds.has(normalized.id)) {
      throw new Error('模板数据待确认')
    }
    seenIds.add(normalized.id)
    return normalized
  })
  if (normalizedRecords.length > count) {
    throw new Error('模板数据待确认')
  }
  return {
    records: normalizedRecords as PromptTemplate[],
    total: count
  }
}

function requireTemplateResult(value: unknown, action: 'create' | 'update') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  const id = Number(value.id)
  const name = typeof value.name === 'string' ? value.name.trim() : ''
  const content = typeof value.content === 'string' ? value.content.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !name || !content) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  return {
    id,
    name,
    content
  }
}

function requireTemplateDetail(value: unknown, action: 'create' | 'update') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  const base = requireTemplateResult(value, action)
  return {
    ...base,
    projectId: normalizeTemplateProjectId(value.projectId, action),
    userId: normalizeOptionalPositiveNumber(value.userId),
    username: normalizeOptionalText(value.username),
    nickname: normalizeOptionalText(value.nickname),
    avatar: normalizeOptionalText(value.avatar),
    tag: normalizeOptionalText(value.tag),
    negativePrompt: normalizeOptionalText(value.negativePrompt),
    modelName: normalizeOptionalText(value.modelName),
    likesCount: normalizeInteractionCount(value.likesCount, action),
    commentsCount: normalizeInteractionCount(value.commentsCount, action),
    liked: normalizeLikedState(value.liked, action),
    status: normalizeTemplateStatus(value.status, action),
    createdAt: requireDateText(value.createdAt, action)
  }
}

function requireLikeToggleResult(value: unknown) {
  if (value === 1 || value === '1' || value === true || value === 'true') {
    return 1
  }
  if (value === 0 || value === '0' || value === false || value === 'false') {
    return 0
  }
  throw new Error('点赞结果待确认')
}

function findLoadedTemplate(id: number) {
  return templates.value?.find(item => Number(item.id) === id) || null
}

function assertTemplateLikeConfirmed(
  previous: { liked: number; likesCount: number; commentsCount: number },
  refreshed: ReturnType<typeof requireTemplateDetail>,
  expectedLiked: number
) {
  if (previous.liked === expectedLiked) {
    throw new Error('点赞结果待确认')
  }
  if (refreshed.liked !== expectedLiked) {
    throw new Error('点赞结果待确认')
  }
  const expectedLikesCount = Math.max(0, previous.likesCount + (expectedLiked === 1 ? 1 : -1))
  if (refreshed.likesCount !== expectedLikesCount) {
    throw new Error('点赞结果待确认')
  }
  if (refreshed.commentsCount !== previous.commentsCount) {
    throw new Error('点赞结果待确认')
  }
}

async function expectTemplateDeleted(id: number) {
  try {
    await loadTemplateDetail(id)
  } catch {
    return
  }
  throw new Error('模板删除结果待确认')
}

async function loadTemplateDetail(id: number) {
  const response = await templateApi.get(id)
  return requireTemplateDetail(getResponseData(response, '模板详情待确认'), 'update')
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeOptionalPositiveNumber(value: unknown) {
  if (value == null || value === '') {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

function normalizeTemplateProjectId(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < PUBLIC_TEMPLATE_LIBRARY_PROJECT_ID) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  return parsed
}

function normalizeInteractionCount(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  return parsed
}

function normalizeLikedState(value: unknown, action: 'create' | 'update') {
  if (value === 1 || value === '1' || value === true || value === 'true') {
    return 1
  }
  if (value === 0 || value === '0' || value === false || value === 'false') {
    return 0
  }
  throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
}

function normalizeTemplateStatus(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  return parsed
}

function requireDateText(value: unknown, action: 'create' | 'update') {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
  return text
}

function buildTemplateExpectation(payload: {
  name: string
  tag?: string
  content: string
  negativePrompt?: string
  modelName?: string
}) {
  return {
    projectId: PUBLIC_TEMPLATE_LIBRARY_PROJECT_ID,
    name: payload.name.trim(),
    tag: normalizeOptionalText(payload.tag),
    content: payload.content.trim(),
    negativePrompt: normalizeOptionalText(payload.negativePrompt),
    modelName: normalizeOptionalText(payload.modelName)
  }
}

function assertTemplateMatches(
  template: ReturnType<typeof requireTemplateDetail>,
  expected: ReturnType<typeof buildTemplateExpectation>,
  action: 'create' | 'update'
) {
  if (
    template.projectId !== expected.projectId
    || template.name !== expected.name
    || template.tag !== expected.tag
    || template.content !== expected.content
    || template.negativePrompt !== expected.negativePrompt
    || template.modelName !== expected.modelName
  ) {
    throw new Error(action === 'create' ? '模板创建结果待确认' : '模板更新结果待确认')
  }
}

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    const response = await templateApi.getTemplates({
      tag: activeTag.value !== 'all' ? activeTag.value : undefined,
      search: searchQuery.value.trim() || undefined,
      sort: sortBy.value,
      page: page.value,
      pageSize: pageSize.value
    })
    const data = requireTemplatePage(getResponseData(response, '模板数据待确认'))
    templates.value = data.records
    totalTemplates.value = data.total
  } catch {
    templates.value = null
    totalTemplates.value = null
  } finally {
    loadingTemplates.value = false
  }
}

async function loadTemplateTags() {
  tagLoadState.value = 'loading'
  try {
    const response = await templateApi.getTags()
    templateTags.value = requireStringList(getResponseData(response, '模板标签待确认'), '模板标签待确认')
    if (activeTag.value !== 'all' && !templateTags.value.includes(activeTag.value)) {
      activeTag.value = 'all'
    }
    tagLoadState.value = 'ready'
  } catch {
    templateTags.value = []
    tagLoadState.value = 'error'
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
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}') as unknown
    currentUserId.value = normalizeOptionalPositiveNumber(isPlainObject(info) ? info.id : null) ?? null
  } catch {}
  loadTemplateTags()
  loadTemplates()
})
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
watch([activeTag, sortBy, searchQuery], () => {
  page.value = 1
  scheduleLoadTemplates()
})
watch([page, pageSize], () => {
  scheduleLoadTemplates(0)
})

const resetForm = () => {
  Object.assign(form, { name: '', tag: '', content: '', negativePrompt: '', modelName: '' })
  editingId.value = null
}
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}

function authorName(tpl: PromptTemplate) {
  return tpl.nickname?.trim() || tpl.username?.trim() || '未知作者'
}

function authorInitial(tpl: PromptTemplate) {
  return (tpl.nickname?.trim() || tpl.username?.trim() || '').slice(0, 1).toUpperCase() || '?'
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function formatInteractionCount(count?: number | null) {
  return typeof count === 'number' ? count : '-'
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
  if (tpl.modelName) query.model = tpl.modelName
  router.push({ path: '/generate/video', query })
  message.success(`已将「${tpl.name}」的提示词与模型带入视频面板`)
}

async function handleLike(tpl: PromptTemplate) {
  try {
    const previousLiked = Number(tpl?.liked)
    const previousLikesCount = Number(tpl?.likesCount)
    const previousCommentsCount = Number(tpl?.commentsCount)
    if (![0, 1].includes(previousLiked) || !Number.isFinite(previousLikesCount) || previousLikesCount < 0 || !Number.isFinite(previousCommentsCount) || previousCommentsCount < 0) {
      throw new Error('点赞结果待确认')
    }
    const response = await request.post(`/api/prompt-templates/${tpl.id}/like`)
    const liked = requireLikeToggleResult(getResponseData(response, '点赞结果待确认'))
    await loadTemplates()
    if (!templates.value) {
      throw new Error('点赞结果待确认')
    }
    const refreshed = await loadTemplateDetail(tpl.id)
    assertTemplateLikeConfirmed({
      liked: previousLiked,
      likesCount: previousLikesCount,
      commentsCount: previousCommentsCount
    }, refreshed, liked)
    const loaded = findLoadedTemplate(tpl.id)
    if (!loaded) {
      throw new Error('点赞结果待确认')
    }
    if (Number(loaded.liked) !== liked || Number(loaded.likesCount) !== refreshed.likesCount || Number(loaded.commentsCount) !== refreshed.commentsCount) {
      throw new Error('点赞结果待确认')
    }
    const target = templates.value.find(item => item.id === tpl.id)
    if (target) {
      Object.assign(target, refreshed)
    }
    if (selectedTemplate.value?.id === tpl.id) {
      selectedTemplate.value = {
        ...selectedTemplate.value,
        ...refreshed
      }
    }
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '点赞失败'))
  }
}

function openComments(tpl: PromptTemplate) {
  selectedTemplate.value = tpl
  showCommentDrawer.value = true
}

function handleCommentCountChange(count: number) {
  if (!selectedTemplate.value) return
  selectedTemplate.value.commentsCount = count
  const target = templates.value?.find(item => item.id === selectedTemplate.value?.id)
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
  form.tag = tpl.tag || ''
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
    const payload = {
      projectId: projectStore.activeProjectId ?? PUBLIC_TEMPLATE_LIBRARY_PROJECT_ID,
      name: form.name,
      tag: form.tag.trim() || undefined,
      content: form.content,
      negativePrompt: form.negativePrompt || undefined,
      modelName: form.modelName || undefined
    }
    const expected = buildTemplateExpectation(payload)
    if (editingId.value) {
      const currentEditingId = editingId.value
      const response = await templateApi.update(currentEditingId, payload)
      const updated = requireTemplateResult(getResponseData(response, '模板更新结果待确认'), 'update')
      await Promise.all([loadTemplateTags(), loadTemplates()])
      const refreshed = await loadTemplateDetail(updated.id)
      assertTemplateMatches(refreshed, expected, 'update')
      const loaded = findLoadedTemplate(currentEditingId)
      if (
        !loaded
        || Number(loaded.id) !== updated.id
        || Number(loaded.projectId) !== refreshed.projectId
        || loaded.name !== refreshed.name
        || loaded.content !== refreshed.content
        || normalizeOptionalText(loaded.tag) !== refreshed.tag
        || normalizeOptionalText(loaded.negativePrompt) !== refreshed.negativePrompt
        || normalizeOptionalText(loaded.modelName) !== refreshed.modelName
      ) {
        throw new Error('模板更新结果待确认')
      }
      message.success('模板已更新！')
    } else {
      const response = await templateApi.createTemplate(payload)
      const created = requireTemplateResult(getResponseData(response, '模板创建结果待确认'), 'create')
      await Promise.all([loadTemplateTags(), loadTemplates()])
      const refreshed = await loadTemplateDetail(created.id)
      assertTemplateMatches(refreshed, expected, 'create')
      const loaded = findLoadedTemplate(created.id)
      if (
        !loaded
        || Number(loaded.projectId) !== refreshed.projectId
        || loaded.name !== refreshed.name
        || loaded.content !== refreshed.content
        || normalizeOptionalText(loaded.tag) !== refreshed.tag
        || normalizeOptionalText(loaded.negativePrompt) !== refreshed.negativePrompt
        || normalizeOptionalText(loaded.modelName) !== refreshed.modelName
      ) {
        throw new Error('模板创建结果待确认')
      }
      message.success('新提示词模板已收录！')
    }
    resetForm()
    showAddModal.value = false
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '保存失败'))
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
        await Promise.all([loadTemplateTags(), loadTemplates()])
        if (templates.value?.some(item => Number(item.id) === id)) {
          throw new Error('模板删除结果待确认')
        }
        await expectTemplateDeleted(id)
        if (selectedTemplate.value?.id === id) {
          selectedTemplate.value = null
        }
        message.success('已删除')
      } catch (err: unknown) {
        message.error(getErrorMessage(err, '删除失败'))
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
.filter-status { margin-top: 10px; font-size: 12px; color: #f59e0b; }
.filter-tabs { max-width: 700px; }
.s-icon { width: 14px; height: 14px; color: var(--text-muted); }

.templates-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; margin-top: 24px; }
.loading-box { display: flex; justify-content: center; padding: 80px 0; }
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
