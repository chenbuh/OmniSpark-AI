<template>
  <div class="stylecards-container">
    <div class="page-header">
      <h2>角色卡 / 风格卡 (Character & Style Cards)</h2>
      <p class="subtitle">公共角色卡与风格卡库，保存可复用的角色设定或风格预设，在生图页跨项目一键应用已记录的提示词和参数。</p>
    </div>

    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-tabs v-model:value="activeType" type="segment" class="filter-tabs">
          <n-tab name="all">全部</n-tab>
          <n-tab name="style">风格预设</n-tab>
          <n-tab name="character">角色卡片</n-tab>
        </n-tabs>
        <n-space>
          <n-select v-model:value="sortBy" :options="sortOptions" style="width:136px;" size="small" />
          <n-input v-model:value="searchQuery" clearable size="small" placeholder="搜索卡片..." style="width:160px;">
            <template #prefix><Search class="inline-icon" /></template>
          </n-input>
          <n-button type="primary" size="medium" @click="showAddModal = true; editingCard = null; resetForm()">
            <template #icon><Plus /></template>新建卡片
          </n-button>
        </n-space>
      </div>
      <div v-if="tagLoadState === 'error'" class="filter-status">风格卡标签待确认，请稍后重试。</div>
    </n-card>

    <div v-if="loadingCards && cards === null" class="loading-box">
      <n-spin size="small" />
    </div>

    <div class="cards-grid" v-else-if="cards && cards.length > 0">
      <div v-for="card in cards" :key="card.id" class="card-item glass-card">
        <div class="card-preview" v-if="card.previewUrl">
          <img :src="resolveAssetUrl(card.previewUrl)" :alt="card.name || '卡片预览图'" class="preview-img" />
        </div>
        <div class="card-preview placeholder-preview" v-else>
          <div class="placeholder-icon-box" :class="card.type">
            <User v-if="card.type === 'character'" class="placeholder-icon" />
            <Palette v-else class="placeholder-icon" />
          </div>
        </div>

        <div class="card-body">
          <div class="card-head">
            <span class="card-name">{{ card.name }}</span>
            <n-tag :type="card.type === 'character' ? 'info' : 'success'" size="tiny" round>
              {{ card.type === 'character' ? '角色' : '风格' }}
            </n-tag>
          </div>
          <p class="card-desc">{{ card.content.substring(0, 80) }}{{ card.content.length > 80 ? '...' : '' }}</p>
          <div class="card-author">
            <n-avatar round size="small" :src="card.avatar || undefined">
              {{ authorInitial(card) }}
            </n-avatar>
            <div class="card-author-meta">
              <span class="card-author-name">{{ authorName(card) }}</span>
              <span class="card-author-time">{{ formatTime(card.createdAt) }}</span>
            </div>
          </div>
          <div class="card-tags" v-if="card.tag">
            <n-tag size="mini" type="warning" round>{{ card.tag }}</n-tag>
          </div>
          <div class="card-meta">
            <n-space :size="8">
              <code v-if="card.modelName">{{ card.modelName }}</code>
              <span class="card-stat"><ThumbsUp class="tiny-icon" /> {{ formatInteractionCount(card.likesCount) }}</span>
              <span class="card-stat"><MessageCircle class="tiny-icon" /> {{ formatInteractionCount(card.commentsCount) }}</span>
            </n-space>
          </div>
        </div>

        <div class="card-actions">
          <n-button type="primary" size="tiny" secondary @click="handleApply(card)">
            <template #icon><Zap /></template>应用
          </n-button>
          <n-button size="tiny" quaternary @click="handleLike(card)" :type="card.liked ? 'primary' : 'default'">
            <template #icon><ThumbsUp /></template>{{ formatInteractionCount(card.likesCount) }}
          </n-button>
          <n-button size="tiny" quaternary @click="openComments(card)">
            <template #icon><MessageCircle /></template>{{ formatInteractionCount(card.commentsCount) }}
          </n-button>
          <n-button v-if="canManage(card)" size="tiny" secondary @click="handleEdit(card)">
            <template #icon><Edit3 /></template>编辑
          </n-button>
          <n-button v-if="canManage(card)" type="error" size="tiny" tertiary @click="handleDelete(card.id)">
            <template #icon><Trash2 /></template>
          </n-button>
        </div>
      </div>
    </div>

    <div class="empty-box" v-else-if="cards !== null">
      <Palette class="empty-icon" />
      <h3>暂无角色卡或风格卡</h3>
      <p>创建一个预设卡片，保存常用的提示词、模型和基础参数，下次一键复用。</p>
    </div>
    <div class="empty-box" v-else>
      <Palette class="empty-icon" />
      <h3>卡片数据待确认</h3>
      <p>当前无法确认角色卡或风格卡列表，请稍后重试。</p>
    </div>

    <div class="pager" v-if="(totalCards ?? 0) > 0">
      <n-pagination
        v-model:page="page"
        :page-size="pageSize"
        :item-count="totalCards"
        show-size-picker
        :page-sizes="pageSizeOptions"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <n-modal v-model:show="showAddModal" preset="card" title="新建卡片" style="width: 540px;" closable>
      <n-form :model="form" label-placement="top" style="margin-top: 10px;">
        <n-row :gutter="16">
          <n-col :span="12">
            <n-form-item label="卡片名称">
              <n-input v-model:value="form.name" placeholder="例如：赛博朋克女主" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="类型">
              <n-select v-model:value="form.type" :options="[{ label: '风格预设', value: 'style' }, { label: '角色卡片', value: 'character' }]" />
            </n-form-item>
          </n-col>
        </n-row>

        <n-form-item label="提示词内容 (Prompt)">
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 3, maxRows: 5 }" placeholder="输入完整的提示词描述..." />
        </n-form-item>

        <n-form-item label="排除要素 (Negative Prompt)">
          <n-input v-model:value="form.negativePrompt" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" placeholder="可选" />
        </n-form-item>

        <n-row :gutter="16">
          <n-col :span="12">
            <n-form-item label="模型名称">
              <n-input v-model:value="form.modelName" placeholder="例如：dall-e-3" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="分类标签">
              <n-select
                v-model:value="form.tag"
                filterable
                tag
                :options="tagOptions"
                placeholder="选择或输入标签"
              />
            </n-form-item>
          </n-col>
        </n-row>

        <n-row :gutter="16">
          <n-col :span="8">
            <n-form-item label="CFG">
              <n-input-number v-model:value="form.cfg" :min="1" :max="20" :step="0.5" placeholder="7.5" />
            </n-form-item>
          </n-col>
          <n-col :span="8">
            <n-form-item label="Steps">
              <n-input-number v-model:value="form.steps" :min="10" :max="50" :step="1" placeholder="25" />
            </n-form-item>
          </n-col>
          <n-col :span="8">
            <n-form-item label="尺寸">
              <n-input v-model:value="form.size" placeholder="1024x1024" />
            </n-form-item>
          </n-col>
        </n-row>

        <n-form-item label="预览图">
          <div class="preview-picker">
            <div class="preview-box" v-if="form.previewUrl">
              <img :src="resolveAssetUrl(form.previewUrl)" alt="卡片预览图" class="preview-thumb" />
              <div class="preview-remove" @click="form.previewUrl = ''">
                <Trash2 class="remove-icon" />
              </div>
            </div>
            <div class="preview-empty" v-else>
              <ImageIcon class="empty-thumb-icon" />
            </div>
            <div class="preview-actions">
              <n-button size="small" secondary @click="openAssetPicker">
                <template #icon><FolderOpen /></template>从资产库选择
              </n-button>
              <n-button size="small" secondary :loading="uploading" @click="triggerUpload">
                <template #icon><Upload /></template>上传本地
              </n-button>
              <input ref="uploadInput" type="file" accept="image/*" style="display:none" @change="handleUploadFile" />
            </div>
          </div>
        </n-form-item>
      </n-form>

      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="handleSave">{{ editingCard ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="showAssetPicker"
      preset="card"
      title="从当前项目资产库选择预览图"
      style="width: 60vw; max-width: 800px;"
    >
      <div class="assets-picker-grid">
        <div
          v-for="asset in imageAssets"
          :key="asset.id"
          class="picker-item"
          @click="handleSelectAsset(asset)"
        >
          <img :src="asset.thumbUrl" :alt="asset.fileName || '可选资产缩略图'" class="picker-thumb" />
          <div class="picker-info">
            <span class="picker-name">{{ asset.fileName }}</span>
          </div>
        </div>
        <div v-if="assetLibraryLoadState === 'error'" class="picker-empty">
          图片资产待确认，请稍后重试。
        </div>
        <div v-else-if="assetLibraryLoadState === 'loading'" class="picker-empty">
          正在加载图片资产...
        </div>
        <div v-else-if="imageAssets.length === 0" class="picker-empty">
          资产库中尚无图片，可先上传本地图片或前往生图页生成。
        </div>
      </div>
    </n-modal>

    <n-drawer v-model:show="showCommentDrawer" :width="460" placement="right">
      <n-drawer-content :title="selectedCard?.name || '卡片互动'" closable>
        <div v-if="selectedCard" class="thread-header">
          <div class="thread-author">
            <n-avatar round size="small" :src="selectedCard.avatar || undefined">
              {{ authorInitial(selectedCard) }}
            </n-avatar>
            <span>{{ authorName(selectedCard) }}</span>
          </div>
          <p class="thread-content">{{ selectedCard.content }}</p>
        </div>
        <PublicCommentThread
          v-if="selectedCard"
          resource-path="/api/style-cards"
          :resource-id="selectedCard.id"
          @count-change="handleCommentCountChange"
        />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { useAssetStore, resolveAssetUrl, type Asset } from '@/store/asset'
import { assetApi } from '@/api/assets'
import request from '@/api/request'
import PublicCommentThread from '@/components/PublicCommentThread.vue'
import { styleCardApi, type StyleCard, type StyleCardPayload } from '@/api/styleCards'
import { Plus, Zap, Edit3, Trash2, User, Palette, Search, Image as ImageIcon, FolderOpen, Upload, ThumbsUp, MessageCircle } from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()
const projectStore = useProjectStore()
const assetStore = useAssetStore()

const activeType = ref('all')
const sortBy = ref('newest')
const searchQuery = ref('')
const loadingCards = ref(false)
const cards = ref<StyleCard[] | null>(null)
const showAddModal = ref(false)
const editingCard = ref<StyleCard | null>(null)
const showAssetPicker = ref(false)
const showCommentDrawer = ref(false)
const uploading = ref(false)
const uploadInput = ref<HTMLInputElement | null>(null)
const selectedCard = ref<StyleCard | null>(null)
const currentUserId = ref<number | null>(null)
const totalCards = ref<number | null>(null)
const styleCardTags = ref<string[]>([])
const assetLibraryLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const tagLoadState = ref<'loading' | 'ready' | 'error'>('loading')
let searchTimer: ReturnType<typeof setTimeout> | null = null
const PUBLIC_STYLE_CARD_LIBRARY_PROJECT_ID = 0

const imageAssets = computed(() => {
  return assetStore
    .getAssetsByProject(projectStore.activeProjectId)
    .filter(a => a.assetType === 'image' || a.assetType === 'reference')
})

const form = reactive({
  name: '',
  type: 'style' as 'character' | 'style',
  content: '',
  negativePrompt: '',
  modelName: '',
  providerId: null as number | null,
  refAssetId: null as number | null,
  paramsJson: '',
  tag: '',
  cfg: null as number | null,
  steps: null as number | null,
  size: '',
  previewUrl: ''
})
const sortOptions = [
  { label: '最新发布', value: 'newest' },
  { label: '最多点赞', value: 'likes' },
  { label: '最多评论', value: 'comments' }
]

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

function requireStyleCardPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('卡片数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('卡片数据待确认')
  }
  const seenIds = new Set<number>()
  const normalizedRecords = records.map((item: unknown) => {
    const normalized = requireStyleCardDetail(item, 'update')
    if (seenIds.has(normalized.id)) {
      throw new Error('卡片数据待确认')
    }
    seenIds.add(normalized.id)
    return normalized
  })
  if (normalizedRecords.length > count) {
    throw new Error('卡片数据待确认')
  }
  return {
    records: normalizedRecords as StyleCard[],
    total: count
  }
}

function requireStyleCardResult(value: unknown, action: 'create' | 'update') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  const id = Number(value.id)
  const name = typeof value.name === 'string' ? value.name.trim() : ''
  const content = typeof value.content === 'string' ? value.content.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !name || !content) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  return {
    id,
    name,
    content
  }
}

function requireStyleCardDetail(value: unknown, action: 'create' | 'update') {
  const base = requireStyleCardResult(value, action)
  const record = value as Record<string, unknown>
  const rawType = record.type
  if (rawType !== 'style' && rawType !== 'character') {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  const type: 'style' | 'character' = rawType
  const likesCount = normalizeInteractionCount(record.likesCount, action)
  const commentsCount = normalizeInteractionCount(record.commentsCount, action)
  const liked = normalizeInteractionLiked(record.liked, action)
  return {
    ...base,
    projectId: normalizeRequiredPositiveNumber(record.projectId, action),
    userId: normalizeOptionalPositiveNumber(record.userId),
    username: normalizeOptionalText(record.username),
    nickname: normalizeOptionalText(record.nickname),
    avatar: normalizeOptionalText(record.avatar),
    type,
    negativePrompt: normalizeOptionalText(record.negativePrompt),
    modelName: normalizeOptionalText(record.modelName),
    providerId: normalizeOptionalPositiveNumber(record.providerId),
    refAssetId: normalizeOptionalPositiveNumber(record.refAssetId),
    cfg: normalizeOptionalNumber(record.cfg),
    steps: normalizeOptionalNumber(record.steps),
    size: normalizeOptionalText(record.size),
    paramsJson: normalizeOptionalText(record.paramsJson),
    tag: normalizeOptionalText(record.tag),
    previewUrl: toRelativeUrl(typeof record.previewUrl === 'string' ? record.previewUrl : ''),
    likesCount,
    commentsCount,
    liked,
    status: normalizeRequiredStatus(record.status, action),
    createdAt: requireDateText(record.createdAt, action)
  }
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

function normalizeOptionalNumber(value: unknown) {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeRequiredPositiveNumber(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < PUBLIC_STYLE_CARD_LIBRARY_PROJECT_ID) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  return parsed
}

function normalizeInteractionCount(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  return parsed
}

function normalizeInteractionLiked(value: unknown, action: 'create' | 'update') {
  if (value === 1 || value === '1' || value === true || value === 'true') {
    return 1
  }
  if (value === 0 || value === '0' || value === false || value === 'false') {
    return 0
  }
  throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
}

function normalizeRequiredStatus(value: unknown, action: 'create' | 'update') {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  return parsed
}

function requireDateText(value: unknown, action: 'create' | 'update') {
  const text = typeof value === 'string' ? value.trim() : ''
  if (!text) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
  return text
}

function buildStyleCardExpectation(payload: StyleCardPayload) {
  return {
    name: payload.name.trim(),
    type: payload.type,
    content: payload.content.trim(),
    negativePrompt: normalizeOptionalText(payload.negativePrompt),
    modelName: normalizeOptionalText(payload.modelName),
    providerId: normalizeOptionalPositiveNumber(payload.providerId),
    refAssetId: normalizeOptionalPositiveNumber(payload.refAssetId),
    cfg: normalizeOptionalNumber(payload.cfg),
    steps: normalizeOptionalNumber(payload.steps),
    size: normalizeOptionalText(payload.size),
    paramsJson: normalizeOptionalText(payload.paramsJson),
    tag: normalizeOptionalText(payload.tag),
    previewUrl: toRelativeUrl(payload.previewUrl || '')
  }
}

function toStyleCardState(detail: ReturnType<typeof requireStyleCardDetail>): Partial<StyleCard> {
  return {
    ...detail,
    negativePrompt: detail.negativePrompt || undefined,
    modelName: detail.modelName || undefined,
    providerId: detail.providerId,
    refAssetId: detail.refAssetId,
    cfg: detail.cfg ?? undefined,
    steps: detail.steps ?? undefined,
    size: detail.size || undefined,
    paramsJson: detail.paramsJson || undefined,
    tag: detail.tag || undefined,
    previewUrl: detail.previewUrl || undefined
  }
}

function assertStyleCardMatches(
  card: ReturnType<typeof requireStyleCardDetail>,
  expected: ReturnType<typeof buildStyleCardExpectation>,
  action: 'create' | 'update'
) {
  if (
    card.name !== expected.name
    || card.type !== expected.type
    || card.content !== expected.content
    || card.negativePrompt !== expected.negativePrompt
    || card.modelName !== expected.modelName
    || card.providerId !== expected.providerId
    || card.refAssetId !== expected.refAssetId
    || card.cfg !== expected.cfg
    || card.steps !== expected.steps
    || card.size !== expected.size
    || card.paramsJson !== expected.paramsJson
    || card.tag !== expected.tag
    || card.previewUrl !== expected.previewUrl
  ) {
    throw new Error(action === 'create' ? '卡片创建结果待确认' : '卡片更新结果待确认')
  }
}

function requireStyleCardLikeResult(value: unknown) {
  if (value === 1 || value === '1' || value === true || value === 'true') {
    return 1
  }
  if (value === 0 || value === '0' || value === false || value === 'false') {
    return 0
  }
  throw new Error('点赞结果待确认')
}

function findLoadedStyleCard(id: number) {
  return cards.value?.find(item => Number(item.id) === id) || null
}

function assertStyleCardLikeConfirmed(
  previous: { liked: number; likesCount: number; commentsCount: number },
  refreshed: ReturnType<typeof requireStyleCardDetail>,
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

async function expectStyleCardDeleted(id: number) {
  try {
    await styleCardApi.get(id)
  } catch {
    return
  }
  throw new Error('卡片删除结果待确认')
}
const tagOptions = computed(() => {
  const values = new Set<string>(styleCardTags.value)
  if (form.tag?.trim()) {
    values.add(form.tag.trim())
  }
  return Array.from(values).map(item => ({ label: item, value: item }))
})

const page = ref(1)
const pageSize = ref(12)
const pageSizeOptions = [12, 24, 48, 96]
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}

async function loadCards() {
  loadingCards.value = true
  try {
    const res = await styleCardApi.list({
      type: activeType.value !== 'all' ? activeType.value : undefined,
      search: searchQuery.value.trim() || undefined,
      sort: sortBy.value,
      page: page.value,
      pageSize: pageSize.value
    })
    const data = requireStyleCardPage(getResponseData(res, '卡片数据待确认'))
    cards.value = data.records
    totalCards.value = data.total
  } catch {
    cards.value = null
    totalCards.value = null
  } finally {
    loadingCards.value = false
  }
}

async function loadAssetLibrary() {
  if (!projectStore.activeProjectId) {
    assetLibraryLoadState.value = 'ready'
    return
  }
  assetLibraryLoadState.value = 'loading'
  try {
    await assetStore.refresh({ projectId: projectStore.activeProjectId })
    assetLibraryLoadState.value = 'ready'
  } catch {
    assetLibraryLoadState.value = 'error'
  }
}

async function loadStyleCardTags() {
  tagLoadState.value = 'loading'
  try {
    const res = await styleCardApi.getTags()
    styleCardTags.value = requireStringList(getResponseData(res, '风格卡标签待确认'), '风格卡标签待确认')
    tagLoadState.value = 'ready'
  } catch {
    styleCardTags.value = []
    tagLoadState.value = 'error'
  }
}

function scheduleLoadCards(delay = 180) {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    loadCards()
  }, delay)
}

onMounted(async () => {
  try {
    const info = JSON.parse(localStorage.getItem('userInfo') || '{}')
    currentUserId.value = normalizeOptionalPositiveNumber(info?.id) ?? null
  } catch {}
  await Promise.all([loadStyleCardTags(), loadCards()])
  await loadAssetLibrary()
})
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
})
watch([activeType, sortBy, searchQuery], () => {
  page.value = 1
  scheduleLoadCards()
})
watch(() => projectStore.activeProjectId, () => {
  void loadAssetLibrary()
})
watch([page, pageSize], () => {
  scheduleLoadCards(0)
})

function authorName(card: StyleCard) {
  return card.nickname?.trim() || card.username?.trim() || '未知作者'
}

function authorInitial(card: StyleCard) {
  return (card.nickname?.trim() || card.username?.trim() || '').slice(0, 1).toUpperCase() || '?'
}

function formatTime(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

function formatInteractionCount(count?: number | null) {
  return typeof count === 'number' ? count : '-'
}

function canManage(card: StyleCard) {
  return !!card.userId && !!currentUserId.value && card.userId === currentUserId.value
}

async function openAssetPicker() {
  if (!projectStore.activeProjectId) {
    message.error('请先选择一个项目空间')
    return
  }
  await loadAssetLibrary()
  showAssetPicker.value = true
}

function handleSelectAsset(asset: Asset) {
  form.previewUrl = toRelativeUrl(asset.fileUrl)
  showAssetPicker.value = false
}

function toRelativeUrl(url: string): string {
  if (!url) return ''
  try {
    return new URL(url, window.location.origin).pathname
  } catch {
    return url.split('?')[0]?.split('#')[0] || url
  }
}

function normalizeFileName(value: string) {
  return value.trim()
}

function assertPreviewAssetMatches(asset: Asset, expected: { projectId: number; fileName: string }) {
  if (asset.projectId !== expected.projectId) {
    throw new Error('预览图上传结果待确认')
  }
  if (normalizeFileName(asset.fileName) !== normalizeFileName(expected.fileName)) {
    throw new Error('预览图上传结果待确认')
  }
  if (asset.assetType !== 'image' && asset.assetType !== 'reference') {
    throw new Error('预览图上传结果待确认')
  }
  if (!asset.fileUrl && !asset.thumbUrl) {
    throw new Error('预览图地址待确认')
  }
}

function triggerUpload() {
  uploadInput.value?.click()
}

async function handleUploadFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const activeProjectId = projectStore.activeProjectId
  if (!activeProjectId) {
    message.error('请先选择一个项目空间')
    input.value = ''
    return
  }
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    input.value = ''
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('projectId', String(activeProjectId))
    formData.append('file', file)
    const res = await assetApi.uploadAsset(formData)
    const uploaded = assetStore.normalizeAsset(getResponseData(res, '预览图上传结果待确认'))
    assertPreviewAssetMatches(uploaded, { projectId: activeProjectId, fileName: file.name })
    await assetStore.refresh({ projectId: activeProjectId })
    const confirmedAsset = imageAssets.value.find(asset => asset.id === uploaded.id)
    if (!confirmedAsset) {
      throw new Error('预览图上传结果待确认')
    }
    assertPreviewAssetMatches(confirmedAsset, { projectId: activeProjectId, fileName: file.name })
    form.previewUrl = toRelativeUrl(confirmedAsset.fileUrl || confirmedAsset.thumbUrl)
    if (!form.previewUrl) {
      throw new Error('预览图地址待确认')
    }
    message.success('预览图已上传')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '上传失败'))
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function resetForm() {
  form.name = ''
  form.type = 'style'
  form.content = ''
  form.negativePrompt = ''
  form.modelName = ''
  form.providerId = null
  form.refAssetId = null
  form.paramsJson = ''
  form.tag = ''
  form.cfg = null
  form.steps = null
  form.size = ''
  form.previewUrl = ''
  editingCard.value = null
}

function handleEdit(card: StyleCard) {
  editingCard.value = card
  form.name = card.name
  form.type = card.type
  form.content = card.content
  form.negativePrompt = card.negativePrompt || ''
  form.modelName = card.modelName || ''
  form.providerId = card.providerId ?? null
  form.refAssetId = card.refAssetId ?? null
  form.paramsJson = card.paramsJson || ''
  form.tag = card.tag || ''
  form.cfg = card.cfg ?? null
  form.steps = card.steps ?? null
  form.size = card.size || ''
  form.previewUrl = card.previewUrl || ''
  showAddModal.value = true
}

async function handleSave() {
  if (!form.name || !form.content) {
    message.error('名称和提示词不能为空')
    return
  }
  const payload = {
    projectId: PUBLIC_STYLE_CARD_LIBRARY_PROJECT_ID,
    name: form.name,
    type: form.type,
    content: form.content,
    negativePrompt: form.negativePrompt || undefined,
    modelName: form.modelName || undefined,
    providerId: form.providerId ?? undefined,
    refAssetId: form.refAssetId ?? undefined,
    cfg: form.cfg ?? undefined,
    steps: form.steps ?? undefined,
    size: form.size || undefined,
    paramsJson: form.paramsJson || undefined,
    tag: form.tag.trim() || undefined,
    previewUrl: form.previewUrl || undefined
  } satisfies StyleCardPayload
  const expected = buildStyleCardExpectation(payload)
  try {
    if (editingCard.value) {
      const res = await styleCardApi.update(editingCard.value.id, payload)
      const updated = requireStyleCardResult(getResponseData(res, '卡片更新结果待确认'), 'update')
      await Promise.all([loadStyleCardTags(), loadCards()])
      assertStyleCardMatches(
        requireStyleCardDetail(getResponseData(await styleCardApi.get(updated.id), '卡片更新结果待确认'), 'update'),
        expected,
        'update'
      )
      message.success('卡片已更新')
    } else {
      const res = await styleCardApi.create(payload)
      const created = requireStyleCardResult(getResponseData(res, '卡片创建结果待确认'), 'create')
      await Promise.all([loadStyleCardTags(), loadCards()])
      assertStyleCardMatches(
        requireStyleCardDetail(getResponseData(await styleCardApi.get(created.id), '卡片创建结果待确认'), 'create'),
        expected,
        'create'
      )
      message.success('卡片已创建')
    }
    showAddModal.value = false
    resetForm()
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '操作失败'))
  }
}

async function handleLike(card: StyleCard) {
  try {
    const previousLiked = Number(card?.liked)
    const previousLikesCount = Number(card?.likesCount)
    const previousCommentsCount = Number(card?.commentsCount)
    if (![0, 1].includes(previousLiked) || !Number.isFinite(previousLikesCount) || previousLikesCount < 0 || !Number.isFinite(previousCommentsCount) || previousCommentsCount < 0) {
      throw new Error('点赞结果待确认')
    }
    const res = await request.post(`/api/style-cards/${card.id}/like`)
    const liked = requireStyleCardLikeResult(getResponseData(res, '点赞结果待确认'))
    await loadCards()
    if (!cards.value) {
      throw new Error('点赞结果待确认')
    }
    const refreshed = requireStyleCardDetail(getResponseData(await styleCardApi.get(card.id), '点赞结果待确认'), 'update')
    assertStyleCardLikeConfirmed({
      liked: previousLiked,
      likesCount: previousLikesCount,
      commentsCount: previousCommentsCount
    }, refreshed, liked)
    const loaded = findLoadedStyleCard(card.id)
    if (!loaded) {
      throw new Error('点赞结果待确认')
    }
    if (Number(loaded.liked) !== liked || Number(loaded.likesCount) !== refreshed.likesCount || Number(loaded.commentsCount) !== refreshed.commentsCount) {
      throw new Error('点赞结果待确认')
    }
    const refreshedState = toStyleCardState(refreshed)
    const target = cards.value.find(item => item.id === card.id)
    if (target) {
      Object.assign(target, refreshedState)
    }
    if (selectedCard.value?.id === card.id) {
      selectedCard.value = {
        ...selectedCard.value,
        ...refreshedState
      }
    }
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '点赞失败'))
  }
}

function openComments(card: StyleCard) {
  selectedCard.value = card
  showCommentDrawer.value = true
}

function handleCommentCountChange(count: number) {
  if (!selectedCard.value) return
  selectedCard.value.commentsCount = count
  const target = cards.value?.find(item => item.id === selectedCard.value?.id)
  if (target) {
    target.commentsCount = count
  }
}

async function handleDelete(id: number) {
  try {
    await styleCardApi.delete(id)
    await Promise.all([loadStyleCardTags(), loadCards()])
    if (cards.value?.some(item => Number(item.id) === id)) {
      throw new Error('卡片删除结果待确认')
    }
    await expectStyleCardDeleted(id)
    if (selectedCard.value?.id === id) {
      selectedCard.value = null
    }
    message.success('已删除')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '删除失败'))
  }
}

function parseStyleCardParamsJson(card: StyleCard) {
  if (!card.paramsJson) {
    return null
  }
  try {
    const parsed = JSON.parse(card.paramsJson)
    return isPlainObject(parsed) ? parsed : null
  } catch {
    return null
  }
}

function toPositiveInteger(value: unknown) {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return Math.round(parsed)
}

function appendQuery(query: Record<string, string>, key: string, value: unknown) {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (normalized) {
    query[key] = normalized
  }
}

function appendPositiveIntegerQuery(query: Record<string, string>, key: string, value: unknown) {
  const normalized = toPositiveInteger(value)
  if (normalized !== null) {
    query[key] = String(normalized)
  }
}

function appendFiniteNumberQuery(query: Record<string, string>, key: string, value: unknown) {
  const parsed = Number(value)
  if (Number.isFinite(parsed)) {
    query[key] = String(parsed)
  }
}

function handleApply(card: StyleCard) {
  const payload = parseStyleCardParamsJson(card)
  const options = payload && isPlainObject(payload.options) ? payload.options : {}
  const query: Record<string, string> = {
    prompt: card.content
  }
  appendQuery(query, 'model', payload?.modelName ?? card.modelName)
  appendQuery(query, 'negPrompt', payload?.negativePrompt ?? card.negativePrompt)
  appendPositiveIntegerQuery(query, 'providerId', payload?.providerId ?? card.providerId)
  appendFiniteNumberQuery(query, 'cfg', options.cfg ?? card.cfg)
  appendPositiveIntegerQuery(query, 'steps', options.steps ?? card.steps)
  appendQuery(query, 'size', payload?.size ?? card.size)
  appendQuery(query, 'resolution', options.resolution)
  appendQuery(query, 'quality', options.quality)
  appendQuery(query, 'aspectRatio', options.aspectRatio)
  appendPositiveIntegerQuery(query, 'aspectWidth', options.aspectWidth)
  appendPositiveIntegerQuery(query, 'aspectHeight', options.aspectHeight)
  const sourceAssetId = payload?.sourceAssetId
    ?? (Array.isArray(payload?.referenceAssetIds) ? payload?.referenceAssetIds[0] : null)
    ?? card.refAssetId
  appendPositiveIntegerQuery(query, 'sourceAssetId', sourceAssetId)
  router.push({
    path: '/generate/image',
    query
  })
  message.success(`已将「${card.name}」的已记录参数带入生图面板`)
}
</script>

<style scoped>
.stylecards-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15, 23, 42, 0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255, 255, 255, 0.08) !important; border-radius: 16px !important; }
.filter-card { margin-bottom: 24px; }
.filter-row { display: flex; justify-content: space-between; align-items: center; }
.filter-status { margin-top: 10px; font-size: 12px; color: #f59e0b; }
.filter-tabs { max-width: 400px; }
.loading-box { display: flex; justify-content: center; padding: 80px 0; }
.cards-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 20px; }
.card-item { display: flex; flex-direction: column; overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; }
.card-item:hover { transform: translateY(-2px); box-shadow: 0 8px 30px rgba(0, 0, 0, 0.3); }
.card-preview { height: 120px; overflow: hidden; }
.preview-img { width: 100%; height: 100%; object-fit: cover; }
.placeholder-preview { display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.03); }
.placeholder-icon-box { width: 56px; height: 56px; border-radius: 16px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.08); }
.placeholder-icon { width: 26px; height: 26px; color: #cbd5e1; }
.card-body { padding: 14px; display: flex; flex-direction: column; gap: 8px; flex: 1; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.card-name { font-size: 14px; font-weight: 700; color: #fff; }
.card-desc { margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.6; }
.card-author { display: flex; align-items: center; gap: 10px; }
.card-author-meta { display: flex; flex-direction: column; gap: 2px; }
.card-author-name { font-size: 12px; color: var(--text-secondary); font-weight: 600; }
.card-author-time { font-size: 11px; color: var(--text-muted); }
.card-meta code { font-size: 10px; color: var(--text-muted); }
.card-stat { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; color: var(--text-muted); }
.tiny-icon { width: 12px; height: 12px; }
.card-actions { display: flex; flex-wrap: wrap; gap: 6px; padding: 12px 14px 14px; border-top: 1px solid rgba(255,255,255,0.06); }
.empty-box { padding: 80px 0; text-align: center; color: #9ca3af; }
.empty-icon { width: 42px; height: 42px; opacity: 0.45; margin-bottom: 10px; }
.preview-picker { display: flex; align-items: center; gap: 14px; }
.preview-box { position: relative; width: 120px; height: 120px; border-radius: 12px; overflow: hidden; border: 1px solid rgba(255,255,255,0.08); }
.preview-thumb { width: 100%; height: 100%; object-fit: cover; }
.preview-remove { position: absolute; top: 6px; right: 6px; width: 28px; height: 28px; border-radius: 50%; background: rgba(15,23,42,0.78); display: flex; align-items: center; justify-content: center; cursor: pointer; }
.remove-icon { width: 14px; height: 14px; color: #fff; }
.preview-empty { width: 120px; height: 120px; border-radius: 12px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.03); border: 1px dashed rgba(255,255,255,0.12); }
.empty-thumb-icon { width: 24px; height: 24px; color: #94a3b8; }
.preview-actions { display: flex; flex-direction: column; gap: 8px; }
.assets-picker-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
.picker-item { border: 1px solid rgba(255,255,255,0.08); border-radius: 12px; overflow: hidden; cursor: pointer; background: rgba(255,255,255,0.03); }
.picker-thumb { width: 100%; height: 120px; object-fit: cover; display: block; }
.picker-info { padding: 8px 10px; }
.picker-name { font-size: 12px; color: var(--text-secondary); }
.picker-empty { grid-column: 1 / -1; padding: 24px 0; text-align: center; color: var(--text-muted); }
.thread-header { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
.thread-author { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-secondary); }
.thread-content { margin: 0; padding: 12px; border-radius: 12px; background: rgba(255,255,255,0.04); color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
</style>
