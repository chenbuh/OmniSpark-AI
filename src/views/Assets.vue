<template>
  <div class="assets-container">
    <div class="page-header">
      <div>
        <h2>共享资产库 (Media Library)</h2>
        <p class="subtitle">统一管理当前空间下的生成图、视频成果与参考素材，支持上传、检索、收藏与一键复用。</p>
      </div>
      <n-space>
        <n-tabs v-model:value="assetTab" type="segment" size="small">
          <n-tab name="own">本空间</n-tab>
          <n-tab name="shared">共享给我</n-tab>
        </n-tabs>
        <n-button secondary :loading="loading" @click="loadAssets">
          <template #icon><RefreshCw /></template>
          刷新资产
        </n-button>
        <n-button type="primary" :loading="uploading" @click="triggerUpload">
          <template #icon><UploadCloud /></template>
          上传参考素材
        </n-button>
      </n-space>
      <input ref="uploadInput" type="file" class="hidden-input" accept="image/*,video/*" @change="handleUploadChange" />
    </div>

    <div class="summary-grid">
      <n-card v-for="item in summaryCards" :key="item.label" class="glass-card summary-card" :bordered="false">
        <span class="summary-label">{{ item.label }}</span>
        <strong class="summary-value">{{ item.value }}</strong>
        <span class="summary-hint">{{ item.hint }}</span>
      </n-card>
    </div>

    <n-card class="glass-card filter-card" :bordered="false">
      <div class="toolbar-top">
        <div class="library-meta">
          <span class="meta-title">{{ currentProjectName || '未选择项目空间' }}</span>
          <span class="meta-desc">{{ libraryMetaDesc }}</span>
        </div>
        <n-space>
          <n-input
            v-model:value="searchKeyword"
            clearable
            placeholder="搜索文件名、提示词或模型"
            style="width: 280px;"
          >
            <template #prefix><Search class="inline-icon" /></template>
          </n-input>
          <n-select v-model:value="sortBy" :options="sortOptions" style="width: 170px;" />
        </n-space>
      </div>

      <n-tabs v-model:value="activeTab" type="segment" animated class="filter-tabs">
        <n-tab name="all">全部素材</n-tab>
        <n-tab v-for="item in assetTypeTabs" :key="item.value" :name="item.value">{{ item.label }}</n-tab>
        <n-tab name="favorite">我的收藏</n-tab>
      </n-tabs>
      <div v-if="assetTypeItemsLoadState === 'error'" class="filter-status">资产分类待确认，请稍后重试。</div>
      <div v-if="assetStatsLoadState === 'error'" class="filter-status">资产汇总待确认，请稍后重试。</div>
      <div v-else-if="assetRecords !== null && filteredTotal === null" class="filter-status">资产总数待确认，请稍后重试。</div>
    </n-card>

    <div v-if="loading && assetRecords === null" class="loading-box">
      <n-spin size="small" />
    </div>

    <div v-else-if="assetRecords && assetRecords.length > 0" class="assets-grid">
      <div v-for="asset in assetRecords" :key="asset.id" class="asset-card" @click="handleOpenDetail(asset)">
        <div class="media-container">
          <video
            v-if="asset.assetType === 'video'"
            :src="asset.fileUrl"
            class="thumb-media"
            muted
            playsinline
            preload="metadata"
          ></video>
          <img v-else :src="asset.thumbUrl" :alt="asset.fileName" class="thumb-media" loading="lazy" />

          <div class="asset-badges">
            <span class="asset-badge" :class="asset.assetType">
              <Video v-if="asset.assetType === 'video'" class="badge-icon" />
              <ImageIcon v-else-if="asset.assetType === 'image'" class="badge-icon" />
              <Paperclip v-else class="badge-icon" />
              {{ assetTypeLabel(asset.assetType) }}
            </span>
            <span v-if="asset.favorite" class="asset-badge favorite">
              <Heart class="badge-icon favorited" />
              收藏
            </span>
          </div>

          <div class="hover-overlay" @click.stop>
            <div class="overlay-buttons">
              <n-button circle secondary class="overlay-btn" @click="handleToggleFavorite(asset)">
                <template #icon>
                  <Heart :class="{ favorited: asset.favorite }" />
                </template>
              </n-button>
              <n-button circle secondary class="overlay-btn" @click="handleDownload(asset)">
                <template #icon><Download /></template>
              </n-button>
              <n-button circle secondary class="overlay-btn" @click="handleOpenOriginal(asset)">
                <template #icon><ExternalLink /></template>
              </n-button>
              <n-button circle type="primary" class="overlay-btn" @click="handleOpenDetail(asset)">
                <template #icon><Eye /></template>
              </n-button>
            </div>
          </div>
        </div>

        <div class="card-info">
          <div class="card-title-row">
            <span class="file-name" :title="asset.fileName">{{ asset.fileName }}</span>
            <span class="size-pill">{{ asset.fileSize }}</span>
          </div>
          <p class="prompt-snippet">
            {{ getAssetDisplayPrompt(asset, getCachedAssetTask(asset)) || '该资产暂无提示词记录，可继续作为素材参考使用。' }}
          </p>
          <div class="meta-row">
            <span>{{ getAssetDisplayModelName(asset, getCachedAssetTask(asset)) || '未记录模型' }}</span>
            <span>{{ formatCompactDate(asset.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="assetRecords !== null" class="empty-box glass-card">
      <Library class="empty-icon" />
      <h3>当前筛选下暂无资产</h3>
      <p>可以先上传参考素材，或回到生图、生视频工作台生成新的内容资产。</p>
      <n-space justify="center">
        <n-button type="primary" @click="triggerUpload">
          <template #icon><UploadCloud /></template>
          上传参考素材
        </n-button>
        <n-button secondary @click="resetFilters">重置筛选</n-button>
      </n-space>
    </div>
    <div v-else class="empty-box glass-card">
      <Library class="empty-icon" />
      <h3>资产数据待确认</h3>
      <p>当前无法确认资产列表，请稍后重试。</p>
    </div>

    <div class="pager" v-if="(filteredTotal ?? 0) > 0">
      <n-pagination
        v-model:page="page"
        :page-size="pageSize"
        :item-count="filteredTotal || 0"
        show-size-picker
        :page-sizes="pageSizeOptions"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <n-drawer v-model:show="showDetailDrawer" :width="560" placement="right" class="glass-drawer">
      <n-drawer-content title="资产详情" closable>
        <div v-if="selectedAsset" class="drawer-inner">
          <div class="drawer-media-box">
            <video
              v-if="selectedAsset.assetType === 'video'"
              :src="selectedAsset.fileUrl"
              controls
              autoplay
              loop
              playsinline
              class="drawer-media"
            ></video>
            <img v-else :src="selectedAsset.fileUrl" :alt="selectedAsset.fileName" class="drawer-media" />
          </div>

          <div class="drawer-action-row">
            <n-button type="primary" secondary class="action-btn" @click="handleReusePrompt">
              <template #icon><Sparkles /></template>
              复用提示词创作
            </n-button>
            <n-button type="warning" secondary class="action-btn" @click="handleApplyAsReference">
              <template #icon><Paperclip /></template>
              设为参考素材
            </n-button>
            <n-button secondary class="action-btn" @click="handleCopyPrompt">
              <template #icon><Copy /></template>
              复制提示词
            </n-button>
          </div>

          <div class="drawer-inline-actions">
            <n-button text type="primary" @click="handleDownload(selectedAsset)">
              <template #icon><Download /></template>
              下载原文件
            </n-button>
            <n-button text type="primary" @click="handleOpenOriginal(selectedAsset)">
              <template #icon><ExternalLink /></template>
              新窗口查看
            </n-button>
            <n-button text type="error" @click="handleDeleteAsset">
              <template #icon><Trash2 /></template>
              删除资产
            </n-button>
          </div>

          <div class="info-list">
            <div class="info-item">
              <span class="info-label">文件名</span>
              <span class="info-val">{{ selectedAsset.fileName }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">资产类型</span>
              <n-tag size="small" :type="selectedAsset.assetType === 'video' ? 'warning' : selectedAsset.assetType === 'reference' ? 'info' : 'success'">
                {{ assetTypeLabel(selectedAsset.assetType) }}
              </n-tag>
            </div>
            <div class="info-item">
              <span class="info-label">文件体积</span>
              <span class="info-val">{{ selectedAsset.fileSize }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">生成时间</span>
              <span class="info-val">{{ selectedAsset.createdAt }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">所属模型</span>
              <span class="info-val">{{ selectedAssetDisplayModelName || '未记录' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">收藏状态</span>
              <n-switch :value="selectedAsset.favorite" @update:value="() => selectedAsset && handleToggleFavorite(selectedAsset!)" />
            </div>
            <div class="info-item prompt-item">
              <span class="info-label">提示词 Prompt</span>
              <div class="prompt-text-block">{{ selectedAssetDisplayPrompt || '该资产暂无提示词记录。' }}</div>
            </div>
          </div>

          <div v-if="versionHistory === null || versionHistory.length > 1" class="detail-section">
            <h4 class="section-title">版本历史 ({{ versionHistory?.length ?? '-' }})</h4>
            <p class="version-subtitle">{{ versionHistorySubtitle }}</p>
            <div v-if="versionHistory && versionHistory.length > 1" class="version-grid">
              <div
                v-for="version in versionHistory"
                :key="version.id"
                class="version-thumb"
                :class="{ 'active-version': version.id === selectedAsset.id }"
                @click="handleSwitchVersion(version)"
              >
                <video
                  v-if="version.assetType === 'video'"
                  :src="version.fileUrl"
                  class="version-media"
                  muted
                  playsinline
                  preload="metadata"
                ></video>
                <img v-else :src="version.thumbUrl" class="version-media" />
                <div class="version-meta">
                  <span>{{ formatCompactDate(version.createdAt) }}</span>
                  <span v-if="version.favorite">⭐</span>
                </div>
              </div>
            </div>
            <n-empty v-else description="版本历史待确认，请稍后重试。" style="padding: 12px 0;" />
          </div>

          <div v-if="selectedAsset.assetType === 'video'" class="detail-section">
            <h4 class="section-title">字幕 / 配音</h4>

            <div v-if="subtitleRows.length > 0">
              <div v-for="sub in subtitleRows" :key="sub.id" class="subtitle-item">
                <div class="subtitle-header">
                  <n-tag size="tiny" type="info">{{ sub.language === 'zh' ? '中文' : sub.language }}</n-tag>
                  <span class="subtitle-status">{{ sub.voiceUrl ? '已生成配音' : '仅字幕' }}</span>
                  <n-space>
                    <n-button size="tiny" secondary @click="openSubtitleEditor(sub.id, sub.srtContent)">
                      <template #icon><Edit3 /></template>
                      编辑
                    </n-button>
                    <n-button size="tiny" type="warning" secondary :loading="voiceLoading" @click="handleGenerateVoice(sub.id)">
                      <template #icon><Volume2 /></template>
                      配音
                    </n-button>
                    <n-button size="tiny" type="error" tertiary @click="handleDeleteSubtitle(sub.id)">
                      <template #icon><Trash2 /></template>
                    </n-button>
                  </n-space>
                </div>
                <n-ellipsis :line-clamp="2" class="subtitle-preview">
                  {{ sub.srtContent }}
                </n-ellipsis>
                <div v-if="sub.voiceUrl" class="voice-player">
                  <audio :src="sub.voiceUrl" controls style="width: 100%; height: 32px;"></audio>
                </div>
              </div>
            </div>
            <n-empty v-else-if="subtitles !== null" description="暂无字幕" style="padding: 12px 0;" />
            <n-empty v-else description="字幕数据待确认，请稍后重试。" style="padding: 12px 0;" />

            <n-space style="margin-top: 8px;">
              <n-button size="small" type="primary" secondary :loading="subGenerating" @click="handleGenerateSubtitle">
                <template #icon><ClosedCaption /></template>
                从视频识别字幕
              </n-button>
            </n-space>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>

    <n-modal v-model:show="showSubEditor" preset="card" title="编辑字幕 (SRT)" style="width: 560px;" closable>
      <n-input
        v-model:value="editSubtitleContent"
        type="textarea"
        :autosize="{ minRows: 8, maxRows: 16 }"
        style="font-family: monospace; font-size: 12px;"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showSubEditor = false">取消</n-button>
          <n-button type="primary" @click="handleSaveSubtitle">保存</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { assetApi } from '@/api/assets'
import { dictApi, type DataDictItem } from '@/api/dicts'
import { taskApi } from '@/api/tasks'
import { subtitleApi, type SubtitleVO } from '@/api/subtitles'
import { useAssetStore, type Asset, resolveAssetUrl } from '@/store/asset'
import { useProjectStore } from '@/store/project'
import { useTaskStore, type GenerationTask } from '@/store/task'
import { buildGenerationReuseLocation } from '@/utils/generationReuse'
import {
  Library,
  RefreshCw,
  UploadCloud,
  Search,
  Video,
  Image as ImageIcon,
  Paperclip,
  Heart,
  Download,
  ExternalLink,
  Eye,
  Sparkles,
  Copy,
  Edit3,
  Volume2,
  Trash2,
  ClosedCaption
} from 'lucide-vue-next'

type SortKey = 'latest' | 'oldest' | 'size' | 'name' | 'favorite'
type AssetStatsState = {
  total: number | null
  imageCount: number | null
  videoCount: number | null
  referenceCount: number | null
  favoriteCount: number | null
}

const route = useRoute()
const router = useRouter()
const message = useMessage()

const projectStore = useProjectStore()
const assetStore = useAssetStore()
const taskStore = useTaskStore()

const loading = ref(false)
const uploading = ref(false)
const activeTab = ref('all')
const searchKeyword = ref('')
const sortBy = ref<SortKey>('latest')
const assetTab = ref<'own' | 'shared'>('own')
const showDetailDrawer = ref(false)
const selectedAsset = ref<Asset | null>(null)
const selectedAssetTask = ref<GenerationTask | null>(null)
const uploadInput = ref<HTMLInputElement | null>(null)
let refreshTimer: number | null = null
let searchTimer: number | null = null
let latestLoadToken = 0
let latestSelectedAssetTaskToken = 0

const assetStats = ref<AssetStatsState>({
  total: null,
  imageCount: null,
  videoCount: null,
  referenceCount: null,
  favoriteCount: null
})
const assetRecords = ref<Asset[] | null>(null)
const filteredTotal = ref<number | null>(null)
const versionHistory = ref<Asset[] | null>(null)
const assetTypeItems = ref<DataDictItem[]>([])
const assetTypeItemsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const assetStatsLoadState = ref<'loading' | 'ready' | 'error'>('loading')

const subtitles = ref<SubtitleVO[] | null>(null)
const subGenerating = ref(false)
const voiceLoading = ref(false)
const showSubEditor = ref(false)
const editSubtitleId = ref<number | null>(null)
const editSubtitleContent = ref('')

const sortOptions = [
  { label: '按最新创建', value: 'latest' },
  { label: '按最早创建', value: 'oldest' },
  { label: '按文件体积', value: 'size' },
  { label: '按文件名称', value: 'name' },
  { label: '按收藏优先', value: 'favorite' }
]

const currentProjectName = computed(() => {
  if (assetTab.value === 'shared') {
    return '共享给我'
  }
  return projectStore.projects.find(item => item.id === projectStore.activeProjectId)?.name || ''
})

const versionHistorySubtitle = computed(() => {
  const asset = selectedAsset.value
  if (!asset) {
    return '按当前项目中的同提示词与同模型名称归并'
  }
  if (!selectedAssetDisplayPrompt.value) {
    return '当前资产没有提示词记录，暂时无法匹配版本历史'
  }
  if (!selectedAssetDisplayModelName.value) {
    return '按当前项目中的同提示词归并，仅匹配未记录模型名的结果'
  }
  return '按当前项目中的同提示词与同模型名称归并'
})

const summaryCards = computed(() => {
  return [
    {
      label: '总资产数',
      value: formatSummaryValue(assetStats.value.total),
      hint: assetStats.value.total === null ? '资产汇总待确认' : '当前空间的全部素材沉淀'
    },
    {
      label: `${assetTypeLabel('image')}成果`,
      value: formatSummaryValue(assetStats.value.imageCount),
      hint: assetStats.value.imageCount === null ? `${assetTypeLabel('image')}数量待确认` : `已沉淀 ${assetTypeLabel('image')}类资产`
    },
    {
      label: `${assetTypeLabel('video')}成果`,
      value: formatSummaryValue(assetStats.value.videoCount),
      hint: assetStats.value.videoCount === null ? `${assetTypeLabel('video')}数量待确认` : `可复用的${assetTypeLabel('video')}内容`
    },
    {
      label: assetTypeLabel('reference'),
      value: formatSummaryValue(assetStats.value.referenceCount),
      hint: assetStats.value.referenceCount === null || assetStats.value.favoriteCount === null
        ? `${assetTypeLabel('reference')}统计待确认`
        : `其中收藏 ${formatSummaryValue(assetStats.value.favoriteCount)} 项`
    }
  ]
})

const assetTypeTabs = computed(() => {
  return assetTypeItems.value.map(item => ({
    label: item.itemName,
    value: item.itemCode
  }))
})

const libraryMetaDesc = computed(() => {
  const suffix = assetTab.value === 'shared' ? '（来自已共享项目）' : '（当前可访问范围）'
  return `当前匹配 ${formatSummaryValue(filteredTotal.value)} / ${formatSummaryValue(assetStats.value.total)} 个资产${suffix}`
})

const page = ref(1)
const pageSize = ref(24)
const pageSizeOptions = [12, 24, 48, 96]
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}

const subtitleRows = computed(() => {
  return (subtitles.value || []).map(item => ({
    ...item,
    voiceUrl: resolveAssetUrl(item.voiceUrl)
  }))
})

const taskLookup = computed(() => {
  const entries = taskStore.tasks.map(task => [task.id, task] as const)
  return new Map<number, GenerationTask>(entries)
})

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function requireAssetResult(value: unknown, action: 'upload' | 'favorite') {
  if (!isPlainObject(value)) {
    throw new Error(action === 'upload' ? '资产上传结果待确认' : '资产状态待确认')
  }
  const normalized = assetStore.normalizeAsset(value)
  if (!Number.isFinite(normalized.id) || normalized.id <= 0 || !normalized.fileUrl || !normalized.thumbUrl) {
    throw new Error(action === 'upload' ? '资产上传结果待确认' : '资产状态待确认')
  }
  return normalized
}

function normalizeFileName(value: string) {
  return value.trim()
}

function assertUploadedAssetMatches(
  asset: Asset,
  expected: { projectId: number; fileName: string; fileType: string }
) {
  if (asset.projectId !== expected.projectId) {
    throw new Error('资产上传结果待确认')
  }
  if (normalizeFileName(asset.fileName) !== normalizeFileName(expected.fileName)) {
    throw new Error('资产上传结果待确认')
  }
  if (expected.fileType.startsWith('video/')) {
    if (asset.assetType !== 'video') {
      throw new Error('资产上传结果待确认')
    }
  } else if (expected.fileType.startsWith('image/')) {
    if (asset.assetType !== 'image' && asset.assetType !== 'reference') {
      throw new Error('资产上传结果待确认')
    }
  }
  if (!asset.fileUrl || !asset.thumbUrl) {
    throw new Error('资产上传结果待确认')
  }
}

function requireSubtitleResult(value: unknown, action: 'generate' | 'update' | 'voice') {
  if (!isPlainObject(value)) {
    if (action === 'generate') throw new Error('字幕识别结果待确认')
    if (action === 'update') throw new Error('字幕更新结果待确认')
    throw new Error('配音结果待确认')
  }
  const assetId = Number(value.assetId)
  const projectId = Number(value.projectId)
  const id = Number(value.id)
  const language = typeof value.language === 'string' ? value.language.trim() : ''
  const status = Number(value.status)
  const createdAt = typeof value.createdAt === 'string' ? value.createdAt : ''
  const srtContent = typeof value.srtContent === 'string' ? value.srtContent : ''
  if (
    !Number.isFinite(id) || id <= 0
    || !Number.isFinite(assetId) || assetId <= 0
    || !Number.isFinite(projectId) || projectId <= 0
    || !language
    || !Number.isFinite(status)
    || !createdAt
  ) {
    if (action === 'generate') throw new Error('字幕识别结果待确认')
    if (action === 'update') throw new Error('字幕更新结果待确认')
    throw new Error('配音结果待确认')
  }
  return {
    id,
    assetId,
    projectId,
    language,
    srtContent,
    status,
    voiceUrl: typeof value.voiceUrl === 'string' ? value.voiceUrl.trim() : '',
    createdAt
  }
}

function formatSummaryValue(value: number | null) {
  return value == null ? '-' : value
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

function getCachedAssetTask(asset?: Asset | null) {
  if (!asset?.taskId) {
    return null
  }
  const cachedTask = taskLookup.value.get(asset.taskId) || null
  return cachedTask?.projectId === asset.projectId ? cachedTask : null
}

function getAssetDisplayPrompt(asset?: Asset | null, task?: GenerationTask | null) {
  return normalizeTaskField(tryParseTaskRequestJson(task)?.prompt) || asset?.prompt || ''
}

function getAssetDisplayModelName(asset?: Asset | null, task?: GenerationTask | null) {
  return normalizeTaskField(tryParseTaskRequestJson(task)?.modelName) || task?.modelName || asset?.modelName || ''
}

const selectedAssetDisplayPrompt = computed(() => getAssetDisplayPrompt(selectedAsset.value, selectedAssetTask.value))
const selectedAssetDisplayModelName = computed(() => getAssetDisplayModelName(selectedAsset.value, selectedAssetTask.value))

const currentAssetType = computed(() => {
  return activeTab.value === 'all' || activeTab.value === 'favorite' ? undefined : activeTab.value
})

const currentFavorite = computed(() => {
  return activeTab.value === 'favorite' ? true : undefined
})

const currentProjectId = computed(() => {
  return assetTab.value === 'own' ? (projectStore.activeProjectId || undefined) : undefined
})

function formatCompactDate(value: string) {
  if (!value || value === '--') return '--'
  return value.length >= 16 ? value.substring(5, 16) : value
}

function requireAssetTypeItems(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('资产分类待确认')
  }
  const seenCodes = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error('资产分类待确认')
    }
    const id = Number(item.id)
    const dictId = Number(item.dictId)
    const itemCode = typeof item.itemCode === 'string' ? item.itemCode.trim() : ''
    const itemName = typeof item.itemName === 'string' ? item.itemName.trim() : ''
    const sortOrder = Number(item.sortOrder)
    const status = Number(item.status)
    if (
      !Number.isFinite(id) || id <= 0
      || !Number.isFinite(dictId) || dictId <= 0
      || !itemCode
      || !itemName
      || !Number.isFinite(sortOrder)
      || !Number.isFinite(status)
      || seenCodes.has(itemCode)
    ) {
      throw new Error('资产分类待确认')
    }
    seenCodes.add(itemCode)
    return {
      id,
      dictId,
      itemCode,
      itemName,
      sortOrder,
      status
    }
  })
}

function requireSubtitleList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('字幕数据待确认')
  }
  const seenIds = new Set<number>()
  return value.map((item: unknown) => {
    const normalized = normalizeSubtitleRecord(item)
    if (seenIds.has(normalized.id)) {
      throw new Error('字幕数据待确认')
    }
    seenIds.add(normalized.id)
    return normalized
  })
}

function normalizeAssetList(value: unknown, errorMessage: string) {
  if (!Array.isArray(value)) {
    throw new Error(errorMessage)
  }
  const seenIds = new Set<number>()
  return value.map((item: unknown) => {
    const normalized = assetStore.normalizeAsset(item)
    if (seenIds.has(normalized.id)) {
      throw new Error(errorMessage)
    }
    seenIds.add(normalized.id)
    return normalized
  })
}

function requireAssetPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('资产列表数据待确认')
  }
  const records = normalizeAssetList(value.records, '资产列表数据待确认')
  const count = Number(value.total)
  if (!Number.isFinite(count) || count < 0 || records.length > count) {
    throw new Error('资产列表数据待确认')
  }
  return {
    records,
    total: count
  }
}

function requireAssetStats(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('资产汇总待确认')
  }
  const total = Number(value.total)
  const imageCount = Number(value.imageCount)
  const videoCount = Number(value.videoCount)
  const referenceCount = Number(value.referenceCount)
  const favoriteCount = Number(value.favoriteCount)
  if (
    !Number.isFinite(total) || total < 0
    || !Number.isFinite(imageCount) || imageCount < 0
    || !Number.isFinite(videoCount) || videoCount < 0
    || !Number.isFinite(referenceCount) || referenceCount < 0
    || !Number.isFinite(favoriteCount) || favoriteCount < 0
    || imageCount + videoCount + referenceCount > total
    || favoriteCount > total
  ) {
    throw new Error('资产汇总待确认')
  }
  return {
    total,
    imageCount,
    videoCount,
    referenceCount,
    favoriteCount
  }
}

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
    const items = requireAssetTypeItems(getResponseData(response, '资产分类待确认'))
    assetTypeItems.value = items
    assetTypeItemsLoadState.value = 'ready'
  } catch {
    assetTypeItems.value = []
    assetTypeItemsLoadState.value = 'error'
  }
  if (activeTab.value !== 'all' && activeTab.value !== 'favorite'
    && !assetTypeItems.value.some(item => item.itemCode === activeTab.value)) {
    activeTab.value = 'all'
  }
}

async function loadAssets() {
  loading.value = true
  const loadToken = ++latestLoadToken
  try {
    assetStatsLoadState.value = 'loading'
    const [pageResult, statsResult] = await Promise.allSettled([
      assetApi.pageAssets({
        scope: assetTab.value,
        projectId: currentProjectId.value,
        assetType: currentAssetType.value,
        favorite: currentFavorite.value,
        search: searchKeyword.value.trim() || undefined,
        sort: sortBy.value,
        page: page.value,
        pageSize: pageSize.value
      }),
      assetApi.getStats({
        scope: assetTab.value,
        projectId: currentProjectId.value
      })
    ])
    if (loadToken !== latestLoadToken) {
      return
    }
    if (pageResult.status !== 'fulfilled') {
      throw new Error('资产列表数据待确认')
    }
    const pageData = requireAssetPage(getResponseData(pageResult.value, '资产列表数据待确认'))
    assetRecords.value = pageData.records
    filteredTotal.value = pageData.total
    if (statsResult.status === 'fulfilled') {
      assetStats.value = requireAssetStats(getResponseData(statsResult.value, '资产汇总待确认'))
      assetStatsLoadState.value = 'ready'
    } else {
      assetStats.value = {
        total: null,
        imageCount: null,
        videoCount: null,
        referenceCount: null,
        favoriteCount: null
      }
      assetStatsLoadState.value = 'error'
    }
    openAssetFromRoute()
  } catch (err: unknown) {
    assetRecords.value = null
    filteredTotal.value = null
    assetStats.value = {
      total: null,
      imageCount: null,
      videoCount: null,
      referenceCount: null,
      favoriteCount: null
    }
    assetStatsLoadState.value = 'error'
    message.error(err instanceof Error && err.message ? err.message : '资产加载失败')
  } finally {
    if (loadToken === latestLoadToken) {
      loading.value = false
    }
  }
}

async function openAssetFromRoute() {
  const assetId = Number(route.query.assetId)
  if (!assetId) return
  if (selectedAsset.value?.id === assetId) return
  const asset = assetRecords.value?.find(item => item.id === assetId)
  if (asset) {
    handleOpenDetail(asset)
    return
  }
  try {
    const response = await assetApi.getAsset(assetId)
    handleOpenDetail(assetStore.normalizeAsset(getResponseData(response, '资产详情待确认')))
  } catch {}
}

async function warmCurrentProjectTasks() {
  const activeProjectId = projectStore.activeProjectId
  if (!activeProjectId || assetTab.value !== 'own') {
    return
  }
  try {
    await taskStore.refresh({ projectId: activeProjectId })
  } catch {}
}

async function loadVersionHistory() {
  if (!selectedAsset.value?.id) {
    versionHistory.value = []
    return
  }
  try {
    const response = await assetApi.getVersions(selectedAsset.value.id, 12)
    versionHistory.value = normalizeAssetList(getResponseData(response, '版本历史待确认'), '版本历史待确认')
  } catch {
    versionHistory.value = null
  }
}

function resetFilters() {
  activeTab.value = 'all'
  searchKeyword.value = ''
  sortBy.value = 'latest'
}

function triggerUpload() {
  uploadInput.value?.click()
}

async function handleUploadChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const activeProjectId = projectStore.activeProjectId
  if (!activeProjectId) {
    message.error('请先选择项目空间')
    target.value = ''
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('projectId', String(activeProjectId))
    formData.append('file', file)
    const response = await assetApi.uploadAsset(formData)
    const uploaded = requireAssetResult(getResponseData(response, '资产上传结果待确认'), 'upload')
    assertUploadedAssetMatches(uploaded, {
      projectId: activeProjectId,
      fileName: file.name,
      fileType: file.type
    })
    await assetStore.refresh({ projectId: activeProjectId, limit: 100 })
    assetTab.value = 'own'
    activeTab.value = uploaded.assetType
    page.value = 1
    await loadAssets()
    const refreshedAsset = assetRecords.value?.find(item => item.id === uploaded.id)
    if (!refreshedAsset) {
      throw new Error('资产上传结果待确认')
    }
    assertUploadedAssetMatches(refreshedAsset, {
      projectId: activeProjectId,
      fileName: file.name,
      fileType: file.type
    })
    handleOpenDetail(refreshedAsset)
    message.success(`素材已上传到共享资产库: ${file.name}`)
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '上传失败')
  } finally {
    uploading.value = false
    target.value = ''
  }
}

function handleOpenDetail(asset: Asset) {
  selectedAsset.value = asset
  showDetailDrawer.value = true
}

async function handleToggleFavorite(asset: Asset) {
  try {
    const updated = await assetStore.toggleFavorite(asset.id)
    assetRecords.value = assetRecords.value?.map(item => item.id === asset.id ? updated : item) || []
    if (selectedAsset.value?.id === asset.id) {
      selectedAsset.value = updated
    }
    await loadAssets()
    const listedAsset = assetRecords.value?.find(item => item.id === asset.id) || null
    const confirmedAsset = listedAsset
      || assetStore.normalizeAsset(getResponseData(await assetApi.getAsset(asset.id), '资产状态待确认'))
    if (confirmedAsset.favorite !== updated.favorite) {
      throw new Error('资产状态待确认')
    }
    if (currentFavorite.value === true && confirmedAsset.favorite === false && listedAsset) {
      throw new Error('资产状态待确认')
    }
    if (selectedAsset.value?.id === asset.id) {
      selectedAsset.value = confirmedAsset
    }
    message.success(confirmedAsset.favorite ? '资产已加入收藏' : '已取消收藏')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '收藏操作失败')
  }
}

function handleDownload(asset: Asset) {
  const anchor = document.createElement('a')
  anchor.href = asset.fileUrl
  anchor.download = asset.fileName
  anchor.target = '_blank'
  anchor.click()
}

function handleOpenOriginal(asset: Asset) {
  window.open(asset.fileUrl, '_blank', 'noopener,noreferrer')
}

async function handleCopyPrompt() {
  const prompt = selectedAssetDisplayPrompt.value
  if (!prompt) {
    message.warning('该资产暂无提示词可复制')
    return
  }
  try {
    await navigator.clipboard.writeText(prompt)
    message.success(normalizeTaskField(tryParseTaskRequestJson(selectedAssetTask.value)?.prompt) ? '真实提示词已复制到剪贴板' : '已复制资产当前已记录的提示词')
  } catch {
    message.error('复制失败，请稍后再试')
  }
}

async function loadAssetTask(asset: Asset) {
  if (!asset.taskId) {
    return null
  }
  const cachedTask = getCachedAssetTask(asset)
  if (cachedTask) {
    return cachedTask
  }
  const response = await taskApi.getTask(asset.taskId)
  const task = taskStore.upsertTask(getResponseData(response, '资产关联任务待确认'))
  if (task.projectId !== asset.projectId) {
    throw new Error('资产关联任务待确认')
  }
  return task
}

async function syncSelectedAssetTask() {
  const asset = selectedAsset.value
  const loadToken = ++latestSelectedAssetTaskToken
  if (!asset?.taskId) {
    selectedAssetTask.value = null
    return
  }
  const cachedTask = getCachedAssetTask(asset)
  selectedAssetTask.value = cachedTask
  try {
    const linkedTask = await loadAssetTask(asset)
    if (loadToken !== latestSelectedAssetTaskToken) {
      return
    }
    selectedAssetTask.value = linkedTask
  } catch {
    if (loadToken === latestSelectedAssetTaskToken) {
      selectedAssetTask.value = cachedTask
    }
  }
}

async function handleReusePrompt() {
  const asset = selectedAsset.value
  if (!asset) return
  const fallbackPrompt = getAssetDisplayPrompt(asset, selectedAssetTask.value)
  const fallbackModelName = getAssetDisplayModelName(asset, selectedAssetTask.value)
  if (!fallbackPrompt && !asset.taskId) {
    message.warning('该资产暂无提示词，暂时无法复用')
    return
  }
  try {
    const linkedTask = await loadAssetTask(asset)
    if (linkedTask) {
      router.push(buildGenerationReuseLocation(linkedTask))
    } else if (asset.assetType === 'video') {
      router.push({
        path: '/generate/video',
        query: { prompt: fallbackPrompt, model: fallbackModelName }
      })
    } else {
      router.push({
        path: '/generate/image',
        query: { prompt: fallbackPrompt, model: fallbackModelName }
      })
    }
    showDetailDrawer.value = false
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '复用参数失败')
    return
  }
}

async function handleApplyAsReference() {
  const asset = selectedAsset.value
  if (!asset) return

  try {
    const linkedTask = await loadAssetTask(asset)
    const fallbackPrompt = getAssetDisplayPrompt(asset, linkedTask || selectedAssetTask.value)
    const fallbackModelName = getAssetDisplayModelName(asset, linkedTask || selectedAssetTask.value)
    if (asset.assetType === 'video') {
      if (linkedTask) {
        router.push(buildGenerationReuseLocation(linkedTask))
        message.success('已将该视频的真实生成参数带入视频工作台')
      } else {
        router.push({
          path: '/generate/video',
          query: {
            prompt: fallbackPrompt,
            model: fallbackModelName
          }
        })
        message.success('已将该视频已记录的提示词与模型带入视频工作台')
      }
    } else {
      if (linkedTask && linkedTask.taskType === 'image') {
        router.push(buildGenerationReuseLocation(linkedTask, {
          overrideSourceAssetId: asset.id
        }))
        message.success('已将该素材设为图生图参考图，并带入真实生成参数')
      } else {
        router.push({
          path: '/generate/image',
          query: {
            sourceAssetId: String(asset.id),
            prompt: fallbackPrompt,
            model: fallbackModelName
          }
        })
        message.success('已将该素材设为图生图参考图，并带入已记录的提示词与模型')
      }
    }
    showDetailDrawer.value = false
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '带入参考素材失败')
    return
  }
}

function handleSwitchVersion(asset: Asset) {
  selectedAsset.value = asset
}

async function loadSubtitles() {
  if (!selectedAsset.value || selectedAsset.value.assetType !== 'video') {
    subtitles.value = []
    return
  }
  try {
    const response = await subtitleApi.list(selectedAsset.value.id)
    subtitles.value = requireSubtitleList(getResponseData(response, '字幕数据待确认'))
  } catch {
    subtitles.value = null
  }
}

async function handleGenerateSubtitle() {
  if (!selectedAsset.value) {
    return
  }
  subGenerating.value = true
  try {
    const response = await subtitleApi.generate({
      assetId: selectedAsset.value.id,
      projectId: selectedAsset.value.projectId,
      prompt: selectedAssetDisplayPrompt.value || undefined,
      language: 'zh'
    })
    const generated = requireSubtitleResult(getResponseData(response, '字幕识别结果待确认'), 'generate')
    await loadSubtitles()
    const refreshedSubtitle = subtitles.value?.find(item => Number(item.id) === generated.id)
    if (
      !refreshedSubtitle
      || Number(refreshedSubtitle.assetId) !== Number(selectedAsset.value.id)
      || Number(refreshedSubtitle.projectId) !== Number(selectedAsset.value.projectId)
      || normalizeOptionalText(refreshedSubtitle.language) !== normalizeOptionalText(generated.language)
      || normalizeOptionalText(refreshedSubtitle.srtContent) !== normalizeOptionalText(generated.srtContent)
    ) {
      throw new Error('字幕识别结果待确认')
    }
    message.success('字幕识别成功')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '字幕识别失败')
  } finally {
    subGenerating.value = false
  }
}

function openSubtitleEditor(id: number, content: string) {
  editSubtitleId.value = id
  editSubtitleContent.value = content
  showSubEditor.value = true
}

async function handleSaveSubtitle() {
  if (!editSubtitleId.value) return
  try {
    const subtitleId = editSubtitleId.value
    const expectedSrtContent = editSubtitleContent.value
    const response = await subtitleApi.update(subtitleId, { srtContent: editSubtitleContent.value })
    const updated = requireSubtitleResult(getResponseData(response, '字幕更新结果待确认'), 'update')
    await loadSubtitles()
    const refreshedSubtitle = subtitles.value?.find(item => Number(item.id) === subtitleId)
    if (
      !refreshedSubtitle
      || normalizeOptionalText(refreshedSubtitle.srtContent) !== normalizeOptionalText(expectedSrtContent)
      || normalizeOptionalText(refreshedSubtitle.srtContent) !== normalizeOptionalText(updated.srtContent)
      || normalizeOptionalText(refreshedSubtitle.language) !== normalizeOptionalText(updated.language)
    ) {
      throw new Error('字幕更新结果待确认')
    }
    message.success('字幕已更新')
    showSubEditor.value = false
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '保存失败')
  }
}

async function handleGenerateVoice(id: number) {
  voiceLoading.value = true
  try {
    const previousVoiceUrl = normalizeOptionalText(subtitles.value?.find(item => Number(item.id) === id)?.voiceUrl)
    const response = await subtitleApi.generateVoice(id)
    const voiced = requireSubtitleResult(getResponseData(response, '配音结果待确认'), 'voice')
    await loadSubtitles()
    const refreshedSubtitle = subtitles.value?.find(item => Number(item.id) === id)
    const resolvedVoiceUrl = refreshedSubtitle ? resolveAssetUrl(refreshedSubtitle.voiceUrl) : ''
    if (
      !refreshedSubtitle
      || normalizeOptionalText(refreshedSubtitle.srtContent) !== normalizeOptionalText(voiced.srtContent)
      || !resolvedVoiceUrl
      || normalizeOptionalText(refreshedSubtitle.voiceUrl) !== normalizeOptionalText(voiced.voiceUrl)
      || normalizeOptionalText(refreshedSubtitle.voiceUrl) === previousVoiceUrl
    ) {
      throw new Error('配音结果待确认')
    }
    message.success('配音生成成功')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '配音失败')
  } finally {
    voiceLoading.value = false
  }
}

async function handleDeleteSubtitle(id: number) {
  try {
    await subtitleApi.delete(id)
    await loadSubtitles()
    if (subtitles.value?.some(item => Number(item.id) === id)) {
      throw new Error('字幕删除结果待确认')
    }
    message.success('字幕已删除')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '删除失败')
  }
}

async function handleDeleteAsset() {
  if (!selectedAsset.value) return
  try {
    const id = selectedAsset.value.id
    const deletingProjectId = selectedAsset.value.projectId
    const previousTotal = filteredTotal.value
    await assetStore.deleteAsset(id)
    const shouldFallbackToPrevPage = page.value > 1 && (assetRecords.value?.length || 0) === 1
    subtitles.value = []
    versionHistory.value = []
    showDetailDrawer.value = false
    selectedAsset.value = null
    if (shouldFallbackToPrevPage) {
      page.value -= 1
    }
    await loadAssets()
    await assetStore.refresh({ projectId: deletingProjectId, limit: 100 })
    if (assetRecords.value?.some(item => item.id === id) || assetStore.assets.some(item => item.id === id)) {
      throw new Error('资产删除结果待确认')
    }
    if (typeof previousTotal === 'number' && typeof filteredTotal.value === 'number' && filteredTotal.value > previousTotal - 1) {
      throw new Error('资产删除结果待确认')
    }
    message.success('资产已删除')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '删除失败')
  }
}

function normalizeSubtitleRecord(value: unknown): SubtitleVO {
  return requireSubtitleResult(value, 'update')
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

watch(selectedAsset, () => {
  void syncSelectedAssetTask()
  loadSubtitles()
  loadVersionHistory()
})

watch([activeTab, assetTab, sortBy], () => {
  if (assetTab.value === 'own') {
    void warmCurrentProjectTasks()
  }
  if (page.value !== 1) {
    page.value = 1
    return
  }
  loadAssets()
})

watch(searchKeyword, () => {
  if (searchTimer !== null) {
    clearTimeout(searchTimer)
  }
  if (page.value !== 1) {
    page.value = 1
  }
  searchTimer = window.setTimeout(() => {
    loadAssets()
  }, 250)
})

watch([page, pageSize], () => {
  loadAssets()
})

watch(() => projectStore.activeProjectId, () => {
  selectedAsset.value = null
  selectedAssetTask.value = null
  showDetailDrawer.value = false
  subtitles.value = []
  versionHistory.value = []
  assetStatsLoadState.value = 'loading'
  void warmCurrentProjectTasks()
  if (page.value !== 1) {
    page.value = 1
    return
  }
  loadAssets()
})

watch(() => route.query.assetId, () => {
  openAssetFromRoute()
})

onMounted(async () => {
  await loadAssetTypeItems()
  await Promise.all([
    loadAssets(),
    warmCurrentProjectTasks()
  ])
  // 自动刷新
  refreshTimer = window.setInterval(() => { if (document.visibilityState === 'visible') loadAssets() }, 30000)
})

onUnmounted(() => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (searchTimer !== null) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})
</script>

<style scoped>
.assets-container {
  padding-bottom: 40px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--text-primary);
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
  max-width: 720px;
}

.hidden-input {
  display: none;
}

.glass-card {
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-label {
  font-size: 12px;
  color: var(--text-muted);
}

.summary-value {
  font-size: 26px;
  color: var(--text-primary);
  line-height: 1;
}

.summary-hint {
  font-size: 11px;
  color: var(--text-muted);
}

.filter-card {
  margin-bottom: 20px;
}

.filter-status {
  margin-top: 10px;
  font-size: 12px;
  color: #f59e0b;
}

.toolbar-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.library-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.meta-desc {
  font-size: 12px;
  color: var(--text-muted);
}

.filter-tabs {
  max-width: 760px;
}

.inline-icon,
.badge-icon {
  width: 14px;
  height: 14px;
}

.assets-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 18px;
}

.loading-box {
  display: flex;
  justify-content: center;
  padding: 48px 0;
}

.pager { display: flex; justify-content: flex-end; margin-top: 20px; }

.asset-card {
  overflow: hidden;
  border-radius: 16px;
  background: var(--card-color);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.asset-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 40px rgba(2, 8, 23, 0.45);
  border-color: rgba(16, 185, 129, 0.35);
}

.media-container {
  position: relative;
  height: 220px;
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.12), transparent 55%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.4), rgba(2, 6, 23, 0.92));
  overflow: hidden;
}

.thumb-media {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.asset-badges {
  position: absolute;
  top: 10px;
  left: 10px;
  right: 10px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  pointer-events: none;
}

.asset-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 11px;
  border-radius: 999px;
  color: #fff;
  background: rgba(15, 23, 42, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.asset-badge.image {
  background: rgba(16, 185, 129, 0.2);
}

.asset-badge.video {
  background: rgba(245, 158, 11, 0.22);
}

.asset-badge.reference {
  background: rgba(59, 130, 246, 0.22);
}

.asset-badge.favorite {
  margin-left: auto;
  background: rgba(239, 68, 68, 0.18);
}

.hover-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(2, 6, 23, 0.08), rgba(2, 6, 23, 0.68));
  opacity: 0;
  transition: opacity 0.25s ease;
}

.asset-card:hover .hover-overlay {
  opacity: 1;
}

.overlay-buttons {
  display: flex;
  gap: 10px;
}

.overlay-btn {
  background: rgba(15, 23, 42, 0.65) !important;
  border: 1px solid rgba(255, 255, 255, 0.12) !important;
  color: #fff !important;
}

:root body.light .overlay-btn {
  background: rgba(0, 0, 0, 0.35) !important;
}

.favorited {
  color: #ef4444 !important;
  fill: #ef4444 !important;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.size-pill {
  font-size: 11px;
  color: var(--text-muted);
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(128, 128, 128, 0.06);
}

.prompt-snippet {
  min-height: 40px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: var(--text-muted);
}

.empty-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 72px 24px;
  text-align: center;
}

.empty-icon {
  width: 48px;
  height: 48px;
  color: var(--text-muted);
}

.empty-box h3 {
  font-size: 20px;
  color: var(--text-primary);
  margin: 0;
}

.empty-box p {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
  max-width: 440px;
}

.glass-drawer {
  background: var(--card-color) !important;
  border-left: 1px solid var(--border-color) !important;
}

.drawer-inner {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.drawer-media-box {
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-secondary);
}

.drawer-media {
  width: 100%;
  max-height: 300px;
  display: block;
  object-fit: contain;
}

.drawer-action-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.action-btn {
  width: 100%;
}

.drawer-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: -4px;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-light);
}

.info-label {
  font-size: 12px;
  color: var(--text-muted);
}

.info-val {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
  text-align: right;
}

.prompt-item {
  align-items: flex-start;
  flex-direction: column;
}

.prompt-text-block {
  width: 100%;
  padding: 12px;
  border-radius: 12px;
  background: rgba(128, 128, 128, 0.04);
  border: 1px solid var(--border-color);
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
}

.detail-section {
  padding-top: 4px;
}

.section-title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.version-subtitle {
  margin: 0 0 12px;
  font-size: 11px;
  color: var(--text-muted);
}

.version-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 10px;
}

.version-thumb {
  overflow: hidden;
  border-radius: 10px;
  border: 2px solid transparent;
  background: rgba(128, 128, 128, 0.04);
  cursor: pointer;
}

.version-thumb:hover {
  border-color: rgba(148, 163, 184, 0.4);
}

.active-version {
  border-color: #10b981;
}

.version-media {
  width: 100%;
  height: 78px;
  display: block;
  object-fit: cover;
}

.version-meta {
  display: flex;
  justify-content: space-between;
  padding: 4px 6px;
  font-size: 10px;
  color: var(--text-muted);
}

.subtitle-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: 12px;
  background: rgba(128, 128, 128, 0.04);
  border: 1px solid var(--border-color);
}

.subtitle-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.subtitle-status {
  flex: 1;
  font-size: 11px;
  color: #10b981;
}

.subtitle-preview {
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-muted);
  font-family: monospace;
}

.voice-player {
  margin-top: 10px;
}

@media (max-width: 1200px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .toolbar-top {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }

  .drawer-action-row {
    grid-template-columns: 1fr;
  }
}
</style>
