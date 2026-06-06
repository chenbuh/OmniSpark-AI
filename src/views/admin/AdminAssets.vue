<template>
  <div class="admin-assets">
    <div class="page-header">
      <h2>全局资产监管 (Asset Supervision)</h2>
      <p class="subtitle">查看和管理所有用户的生成资产，点击缩略图可预览完整文件。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <n-space align="center">
          <n-select v-model:value="typeFilter" :options="typeOptions" placeholder="资产类型" style="width:140px" clearable @update:value="reload" />
          <n-input v-model:value="searchText" placeholder="搜索文件名..." style="width:200px;" clearable @update:value="reload" />
        </n-space>
        <span class="count">共 {{ totalDisplay }} 个资产</span>
      </div>
      <div v-if="assetTypeItemsLoadState === 'error'" class="status-note">资产类型选项待确认，请稍后重试。</div>

      <div v-if="loadingAssets && assets === null" class="loading-box">
        <n-spin size="small" />
      </div>

      <div class="assets-grid" v-else-if="assets && assets.length > 0">
        <div v-for="a in assets" :key="a.id" class="asset-card" @click="openPreview(a)">
          <div class="asset-thumb">
            <img v-if="a.assetType === 'image' || a.assetType === 'reference'" :src="a.thumbUrl || a.fileUrl" class="thumb-img" loading="lazy" />
            <div v-else class="video-thumb">
              <Video class="video-icon" />
              <span class="thumb-type">视频</span>
            </div>
            <div class="asset-overlay">
              <Eye class="overlay-icon" />
            </div>
          </div>
          <div class="asset-info">
            <span class="asset-id">#{{ a.id }}</span>
            <n-tag size="tiny" :type="a.assetType==='image'?'success':a.assetType==='video'?'warning':'default'" round>{{ assetTypeLabel(a.assetType) }}</n-tag>
            <span class="asset-size">{{ a.fileSize || '-' }}</span>
          </div>
          <div class="asset-project">项目 #{{ a.projectId }}</div>
        </div>
      </div>

      <n-empty v-else-if="assets !== null" description="暂无资产" style="padding:40px 0;" />
      <n-empty v-else description="资产数据待确认，请稍后重试。" style="padding:40px 0;" />

      <div class="pager" v-if="(total ?? 0) > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadAssets" />
      </div>
    </n-card>

    <!-- 预览弹窗 -->
    <n-modal v-model:show="showPreview" preset="card" style="width:80vw;max-width:900px;" closable>
      <template #header>
        <span>{{ previewAsset?.fileName }} <n-tag size="tiny" style="margin-left:8px;">#{{ previewAsset?.id }}</n-tag></span>
      </template>
      <div class="preview-body">
        <img v-if="previewAsset?.assetType === 'image' || previewAsset?.assetType === 'reference'" :src="previewAsset?.fileUrl" class="preview-img" />
        <video v-else-if="previewAsset?.assetType === 'video'" :src="previewAsset?.fileUrl" controls autoplay class="preview-video"></video>
      </div>
      <div class="preview-meta" v-if="previewAsset">
        <n-descriptions :column="3" size="small" label-placement="left" bordered>
          <n-descriptions-item label="ID">{{ previewAsset.id }}</n-descriptions-item>
          <n-descriptions-item label="项目">{{ previewAsset.projectId }}</n-descriptions-item>
          <n-descriptions-item label="类型">{{ assetTypeLabel(previewAsset.assetType) }}</n-descriptions-item>
          <n-descriptions-item label="文件名">{{ previewAsset.fileName }}</n-descriptions-item>
          <n-descriptions-item label="大小">{{ previewAsset.fileSize || '-' }}</n-descriptions-item>
          <n-descriptions-item label="时间">{{ String(previewAsset.createdAt||'').replace('T',' ').substring(0,19) }}</n-descriptions-item>
          <n-descriptions-item label="模型">{{ previewDisplayModelName || '未记录' }}</n-descriptions-item>
        </n-descriptions>
        <div class="preview-prompt" v-if="previewDisplayPrompt">
          <strong>Prompt:</strong> {{ previewDisplayPrompt }}
        </div>
        <div class="preview-prompt" v-if="previewDisplayNegativePrompt">
          <strong>Negative:</strong> {{ previewDisplayNegativePrompt }}
        </div>
      </div>
      <template #footer>
        <n-button size="small" @click="showPreview = false">关闭</n-button>
        <n-button size="small" type="primary" @click="downloadAsset">下载原文件</n-button>
        <n-button size="small" type="error" @click="handleDeletePreview">删除</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Eye, Video } from 'lucide-vue-next'
import { dictApi, type DataDictItem } from '@/api/dicts'
import request from '@/api/request'
import { taskApi } from '@/api/tasks'
import { useTaskStore, type GenerationTask } from '@/store/task'

type AssetType = 'image' | 'video' | 'reference'

interface AdminAssetRecord {
  id: number
  projectId: number
  taskId: number | null
  assetType: AssetType
  fileName: string
  fileUrl: string
  thumbUrl: string
  fileSize: number | null
  prompt: string
  modelName: string
  createdAt: string
}

const message = useMessage()
const taskStore = useTaskStore()
const loadingAssets = ref(true)
const assets = ref<AdminAssetRecord[] | null>(null)
const typeFilter = ref<AssetType | null>(null)
const searchText = ref('')
const showPreview = ref(false)
const previewAsset = ref<AdminAssetRecord | null>(null)
const previewTask = ref<GenerationTask | null>(null)
const page = ref(1)
const pageSize = 12
const total = ref<number | null>(null)
const assetTypeItems = ref<DataDictItem[]>([])
const assetTypeItemsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const totalDisplay = computed(() => total.value == null ? '-' : total.value)
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
const ASSET_TYPES: AssetType[] = ['image', 'video', 'reference']
let latestPreviewTaskToken = 0

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error('资产数据待确认')
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

function normalizeOptionalNumber(value: unknown) {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

function normalizeOptionalPositiveNumber(value: unknown) {
  const normalized = normalizeOptionalNumber(value)
  return normalized != null && normalized > 0 ? normalized : null
}

function normalizeAssetType(value: unknown): AssetType {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!ASSET_TYPES.includes(normalized as AssetType)) {
    throw new Error('资产数据待确认')
  }
  return normalized as AssetType
}

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

function normalizeAssetRecord(value: unknown): AdminAssetRecord {
  if (!isPlainObject(value)) {
    throw new Error('资产数据待确认')
  }
  const id = Number(value.id)
  const projectId = normalizeOptionalPositiveNumber(value.projectId)
  const taskId = normalizeOptionalPositiveNumber(value.taskId)
  const assetType = normalizeAssetType(value.assetType)
  const fileName = typeof value.fileName === 'string' ? value.fileName.trim() : ''
  const fileUrl = typeof value.fileUrl === 'string' ? value.fileUrl.trim() : ''
  const createdAt = typeof value.createdAt === 'string' ? value.createdAt.trim() : ''
  const fileSize = normalizeOptionalNumber(value.fileSize)
  if (!Number.isFinite(id) || id <= 0 || projectId == null || !fileName || !fileUrl || !createdAt || (fileSize != null && fileSize < 0)) {
    throw new Error('资产数据待确认')
  }
  return {
    id,
    projectId,
    taskId,
    assetType,
    fileName,
    fileUrl,
    thumbUrl: typeof value.thumbUrl === 'string' ? value.thumbUrl.trim() : '',
    fileSize,
    prompt: typeof value.prompt === 'string' ? value.prompt : '',
    modelName: typeof value.modelName === 'string' ? value.modelName.trim() : '',
    createdAt
  }
}

function requireAssetPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('资产数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('资产数据待确认')
  }
  const normalizedRecords = records.map((item: unknown) => normalizeAssetRecord(item))
  const ids = new Set<number>()
  normalizedRecords.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('资产数据待确认')
    }
    ids.add(item.id)
  })
  if (normalizedRecords.length > count) {
    throw new Error('资产数据待确认')
  }
  return {
    records: normalizedRecords,
    total: count
  }
}

const previewDisplayPrompt = computed(() => {
  return normalizeTaskField(tryParseTaskRequestJson(previewTask.value)?.prompt) || previewAsset.value?.prompt || ''
})

const previewDisplayNegativePrompt = computed(() => {
  return normalizeTaskField(tryParseTaskRequestJson(previewTask.value)?.negativePrompt) || previewTask.value?.negativePrompt || ''
})

const previewDisplayModelName = computed(() => {
  return normalizeTaskField(tryParseTaskRequestJson(previewTask.value)?.modelName) || previewTask.value?.modelName || previewAsset.value?.modelName || ''
})

const typeOptions = computed(() => [
  ...assetTypeItems.value.map(item => ({ label: item.itemName, value: item.itemCode }))
])

onMounted(async () => {
  await loadAssetTypeItems()
  await loadAssets()
})

function assetTypeLabel(assetType?: string | null) {
  const normalized = String(assetType || '').trim()
  if (!normalized) {
    return '未提供分类'
  }
  return assetTypeItems.value.find(item => item.itemCode === normalized)?.itemName || normalized
}

async function loadAssetTypeItems() {
  assetTypeItemsLoadState.value = 'loading'
  try {
    const response = await dictApi.getItems('asset_category')
    const items = requireAssetTypeItems(getResponseData(response))
    assetTypeItems.value = items
    assetTypeItemsLoadState.value = 'ready'
  } catch {
    assetTypeItems.value = []
    assetTypeItemsLoadState.value = 'error'
  }
}

function requireAssetTypeItems(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('资产类型待确认')
  }
  const seenCodes = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error('资产类型待确认')
    }
    const id = Number(item.id)
    const dictId = Number(item.dictId)
    const itemCode = typeof item.itemCode === 'string' ? item.itemCode.trim() : ''
    const itemName = typeof item.itemName === 'string' ? item.itemName.trim() : ''
    const sortOrder = normalizeOptionalNumber(item.sortOrder)
    const status = normalizeOptionalNumber(item.status)
    if (
      !Number.isFinite(id)
      || id <= 0
      || !Number.isFinite(dictId)
      || dictId <= 0
      || !itemCode
      || !itemName
      || seenCodes.has(itemCode)
      || (sortOrder != null && sortOrder < 0)
      || (status != null && status < 0)
    ) {
      throw new Error('资产类型待确认')
    }
    seenCodes.add(itemCode)
    const normalized: DataDictItem = {
      id,
      dictId,
      itemCode,
      itemName
    }
    if (sortOrder != null) {
      normalized.sortOrder = sortOrder
    }
    if (status != null) {
      normalized.status = status
    }
    return normalized
  })
}

// 过滤变化时回到第 1 页
function reload() {
  page.value = 1
  loadAssets()
}

async function loadAssets(noCache = false) {
  loadingAssets.value = true
  try {
    const params: Record<string, number | string> = { page: page.value, pageSize }
    if (typeFilter.value) params.assetType = typeFilter.value
    if (searchText.value) params.search = searchText.value
    const response = await request.get<unknown>('/api/admin/assets', {
      params,
      headers: noCache ? NO_CACHE_HEADERS : undefined
    })
    const data = requireAssetPage(getResponseData(response))
    assets.value = data.records
    total.value = data.total
  } catch (err: unknown) {
    assets.value = null
    total.value = null
    message.error(getErrorMessage(err, '加载资产失败'))
  } finally {
    loadingAssets.value = false
  }
}

async function isAssetDeleted(id: number) {
  try {
    await request.get(`/api/admin/assets/${id}`, { headers: NO_CACHE_HEADERS })
    return false
  } catch (err: unknown) {
    const errorMessage = getErrorMessage(err, '')
    if (errorMessage.includes('资产不存在')) {
      return true
    }
    throw err
  }
}

async function handleDelete(id: number) {
  try {
    const previousTotal = total.value
    await request.delete(`/api/admin/assets/${id}`)
    if ((assets.value?.length || 0) === 1 && page.value > 1) page.value--
    await loadAssets(true)
    if (assets.value?.some(asset => Number(asset.id) === id)) {
      throw new Error('资产删除结果待确认')
    }
    if (!(await isAssetDeleted(id))) {
      throw new Error('资产删除结果待确认')
    }
    if (previousTotal != null && total.value == null) {
      throw new Error('资产删除结果待确认')
    }
    if (previousTotal != null && total.value !== Math.max(0, previousTotal - 1)) {
      throw new Error('资产删除结果待确认')
    }
    if (Number(previewAsset.value?.id) === id) {
      previewAsset.value = null
    }
    message.success('已删除')
  } catch (err: unknown) { message.error(getErrorMessage(err, '删除失败')) }
}

function handleDeletePreview() {
  if (!previewAsset.value) {
    return
  }
  const assetId = previewAsset.value.id
  showPreview.value = false
  previewTask.value = null
  void handleDelete(assetId)
}

async function loadPreviewTask(asset: AdminAssetRecord) {
  const loadToken = ++latestPreviewTaskToken
  if (!asset.taskId) {
    previewTask.value = null
    return
  }
  try {
    const response = await taskApi.getTask(asset.taskId)
    const task = taskStore.normalizeTask(getResponseData(response))
    if (task.projectId !== asset.projectId) {
      throw new Error('资产关联任务待确认')
    }
    if (loadToken !== latestPreviewTaskToken) {
      return
    }
    previewTask.value = task
  } catch {
    if (loadToken === latestPreviewTaskToken) {
      previewTask.value = null
    }
  }
}

function openPreview(asset: AdminAssetRecord) {
  previewAsset.value = asset
  previewTask.value = null
  showPreview.value = true
  void loadPreviewTask(asset)
}

function downloadAsset() {
  if (!previewAsset.value) return
  const a = document.createElement('a')
  a.href = previewAsset.value.fileUrl
  a.download = previewAsset.value.fileName
  a.target = '_blank'
  a.click()
}
</script>

<style scoped>
.admin-assets { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #f59e0b; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.count { font-size: 12px; color: var(--text-muted); }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }

.assets-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.asset-card {
  background: rgba(128,128,128,0.03); border: 1px solid var(--border-color);
  border-radius: 12px; overflow: hidden; cursor: pointer; transition: all .2s;
}
.asset-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.2); border-color: #10b981; }
.asset-thumb { position: relative; height: 140px; overflow: hidden; background: rgba(128,128,128,0.05); }
.thumb-img { width: 100%; height: 100%; object-fit: cover; }
.video-thumb { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 6px; }
.video-icon { width: 32px; height: 32px; opacity: 0.4; }
.thumb-type { font-size: 11px; color: var(--text-muted); }
.asset-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity .2s; }
.asset-card:hover .asset-overlay { opacity: 1; }
.overlay-icon { width: 28px; height: 28px; color: #fff; }
.asset-info { display: flex; align-items: center; gap: 6px; padding: 8px 10px; font-size: 12px; }
.asset-id { color: var(--text-primary); font-weight: 600; }
.asset-size { color: var(--text-muted); margin-left: auto; }
.asset-project { padding: 0 10px 8px; font-size: 10px; color: var(--text-muted); }

.preview-body { display: flex; justify-content: center; margin-bottom: 16px; }
.preview-img { max-width: 100%; max-height: 60vh; border-radius: 8px; }
.preview-video { max-width: 100%; max-height: 60vh; border-radius: 8px; }
.preview-meta { display: flex; flex-direction: column; gap: 12px; }
.preview-prompt { font-size: 13px; color: var(--text-secondary); line-height: 1.5; padding: 10px; background: rgba(128,128,128,0.05); border-radius: 8px; }
</style>
