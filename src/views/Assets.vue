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
          <span class="meta-desc">当前匹配 {{ filteredAssets.length }} / {{ scopedAssets.length }} 个资产（含全局历史素材）</span>
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
        <n-tab name="image">生成图像</n-tab>
        <n-tab name="video">生成视频</n-tab>
        <n-tab name="reference">参考素材</n-tab>
        <n-tab name="favorite">我的收藏</n-tab>
      </n-tabs>
    </n-card>

    <div v-if="filteredAssets.length > 0" class="assets-grid">
      <div v-for="asset in pagedAssets" :key="asset.id" class="asset-card" @click="handleOpenDetail(asset)">
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
              {{ assetTypeLabelMap[asset.assetType] }}
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
            {{ asset.prompt || '该资产暂无提示词记录，可继续作为素材参考使用。' }}
          </p>
          <div class="meta-row">
            <span>{{ asset.modelName || '未记录模型' }}</span>
            <span>{{ formatCompactDate(asset.createdAt) }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty-box glass-card">
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

    <div class="pager" v-if="filteredAssets.length > 0">
      <n-pagination
        v-model:page="page"
        :page-size="pageSize"
        :item-count="filteredAssets.length"
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
                {{ assetTypeLabelMap[selectedAsset.assetType] }}
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
              <span class="info-val">{{ selectedAsset.modelName || '未记录' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">收藏状态</span>
              <n-switch :value="selectedAsset.favorite" @update:value="() => selectedAsset && handleToggleFavorite(selectedAsset!)" />
            </div>
            <div class="info-item prompt-item">
              <span class="info-label">提示词 Prompt</span>
              <div class="prompt-text-block">{{ selectedAsset.prompt || '该资产暂无提示词记录。' }}</div>
            </div>
          </div>

          <div v-if="versionHistory.length > 1" class="detail-section">
            <h4 class="section-title">版本历史 ({{ versionHistory.length }})</h4>
            <p class="version-subtitle">同一提示词与模型下的其他输出版本</p>
            <div class="version-grid">
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
            <n-empty v-else description="暂无字幕" style="padding: 12px 0;" />

            <n-space style="margin-top: 8px;">
              <n-button size="small" type="primary" secondary :loading="subGenerating" @click="handleGenerateSubtitle">
                <template #icon><ClosedCaption /></template>
                从提示词生成字幕
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
import request from '@/api/request'
import { subtitleApi, type SubtitleVO } from '@/api/subtitles'
import { useAssetStore, type Asset, resolveAssetUrl } from '@/store/asset'
import { useProjectStore } from '@/store/project'
import { useUserStore } from '@/store/user'
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

const assetTypeLabelMap: Record<Asset['assetType'], string> = {
  image: '图像',
  video: '视频',
  reference: '参考素材'
}

const route = useRoute()
const router = useRouter()
const message = useMessage()

const projectStore = useProjectStore()
const assetStore = useAssetStore()
const userStore = useUserStore()

const loading = ref(false)
const uploading = ref(false)
const activeTab = ref<'all' | 'image' | 'video' | 'reference' | 'favorite'>('all')
const searchKeyword = ref('')
const sortBy = ref<SortKey>('latest')
const assetTab = ref('own')
const sharedAssets = ref<Asset[]>([])
const showDetailDrawer = ref(false)
const selectedAsset = ref<Asset | null>(null)
const uploadInput = ref<HTMLInputElement | null>(null)
let refreshTimer: number | null = null

const subtitles = ref<SubtitleVO[]>([])
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
  return projectStore.projects.find(item => item.id === projectStore.activeProjectId)?.name || ''
})

const scopedAssets = computed(() => {
  const activeProjectId = projectStore.activeProjectId
  if (!activeProjectId) {
    return assetStore.assets
  }
  return assetStore.assets.filter(item => item.projectId === activeProjectId || item.projectId === 0)
})

const summaryCards = computed(() => {
  const assets = scopedAssets.value
  const imageCount = assets.filter(item => item.assetType === 'image').length
  const videoCount = assets.filter(item => item.assetType === 'video').length
  const referenceCount = assets.filter(item => item.assetType === 'reference').length
  const favoriteCount = assets.filter(item => item.favorite).length

  return [
    { label: '总资产数', value: assets.length, hint: '当前空间的全部素材沉淀' },
    { label: '图像成果', value: imageCount, hint: '生成图像与关键画面' },
    { label: '视频成果', value: videoCount, hint: '可复用的视频片段与动画' },
    { label: '参考素材', value: referenceCount + favoriteCount, hint: `其中收藏 ${favoriteCount} 项` }
  ]
})

const filteredAssets = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  // 根据 tab 选择数据源
  const source = assetTab.value === 'shared' ? sharedAssets.value : scopedAssets.value
  let list = [...source]

  if (activeTab.value === 'favorite') {
    list = list.filter(item => item.favorite)
  } else if (activeTab.value !== 'all') {
    list = list.filter(item => item.assetType === activeTab.value)
  }

  if (keyword) {
    list = list.filter(item => {
      return [
        item.fileName,
        item.prompt,
        item.modelName,
        assetTypeLabelMap[item.assetType]
      ].some(value => String(value || '').toLowerCase().includes(keyword))
    })
  }

  list.sort((a, b) => {
    switch (sortBy.value) {
      case 'oldest':
        return toTimestamp(a.createdAt) - toTimestamp(b.createdAt)
      case 'size':
        return b.fileSizeBytes - a.fileSizeBytes
      case 'name':
        return a.fileName.localeCompare(b.fileName, 'zh-CN')
      case 'favorite':
        return Number(b.favorite) - Number(a.favorite) || toTimestamp(b.createdAt) - toTimestamp(a.createdAt)
      case 'latest':
      default:
        return toTimestamp(b.createdAt) - toTimestamp(a.createdAt)
    }
  })

  return list
})

// 前端分页(assetStore 全量不动,仅渲染层切片)
const page = ref(1)
const pageSize = ref(24)
const pageSizeOptions = [12, 24, 48, 96]
const pagedAssets = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredAssets.value.slice(start, start + pageSize.value)
})
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
}
// 切换 tab / 过滤 / 排序 / 项目时回到第 1 页
watch([activeTab, assetTab, searchKeyword, sortBy, () => projectStore.activeProjectId], () => { page.value = 1 })

const versionHistory = computed(() => {
  const current = selectedAsset.value
  if (!current?.prompt) return []
  return scopedAssets.value
    .filter(item =>
      item.prompt === current.prompt &&
      item.modelName === current.modelName &&
      ['image', 'video', 'reference'].includes(item.assetType)
    )
    .sort((a, b) => toTimestamp(b.createdAt) - toTimestamp(a.createdAt))
})

const subtitleRows = computed(() => {
  return subtitles.value.map(item => ({
    ...item,
    voiceUrl: resolveAssetUrl(item.voiceUrl)
  }))
})

function toTimestamp(value: string): number {
  const ts = Date.parse(value.replace(' ', 'T'))
  return Number.isFinite(ts) ? ts : 0
}

function formatCompactDate(value: string) {
  if (!value || value === '--') return '--'
  return value.length >= 16 ? value.substring(5, 16) : value
}

async function loadAssets() {
  loading.value = true
  try {
    await assetStore.refresh()
    // 加载共享给我的资产
    const userId = userStore.userInfo?.id
    if (userId) {
      try {
        const sharedRes = await request.get('/api/assets/shared', { params: { userId } })
        sharedAssets.value = (sharedRes as any).data || []
      } catch {}
    }
    openAssetFromRoute()
  } catch (err: any) {
    message.error(err.message || '资产加载失败')
  } finally {
    loading.value = false
  }
}

function openAssetFromRoute() {
  const assetId = Number(route.query.assetId)
  if (!assetId) return
  const asset = assetStore.assets.find(item => item.id === assetId)
  if (asset) {
    handleOpenDetail(asset)
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
  if (!projectStore.activeProjectId) {
    message.error('请先选择项目空间')
    target.value = ''
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('projectId', String(projectStore.activeProjectId))
    formData.append('file', file)
    const res = await assetApi.uploadAsset(formData)
    const uploaded = assetStore.normalizeAsset(res.data)
    await assetStore.refresh()
    activeTab.value = 'reference'
    handleOpenDetail(assetStore.assets.find(item => item.id === uploaded.id) || uploaded)
    message.success(`素材已上传到共享资产库: ${file.name}`)
  } catch (err: any) {
    message.error(err.message || '上传失败')
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
    if (selectedAsset.value?.id === asset.id) {
      selectedAsset.value = updated
    }
    message.success(updated.favorite ? '资产已加入收藏' : '已取消收藏')
  } catch (err: any) {
    message.error(err.message || '收藏操作失败')
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
  if (!selectedAsset.value?.prompt) {
    message.warning('该资产暂无提示词可复制')
    return
  }
  try {
    await navigator.clipboard.writeText(selectedAsset.value.prompt)
    message.success('提示词已复制到剪贴板')
  } catch {
    message.error('复制失败，请稍后再试')
  }
}

function handleReusePrompt() {
  const asset = selectedAsset.value
  if (!asset) return
  if (!asset.prompt) {
    message.warning('该资产暂无提示词，暂时无法复用')
    return
  }
  if (asset.assetType === 'video') {
    router.push({
      path: '/generate/video',
      query: { prompt: asset.prompt, model: asset.modelName }
    })
  } else {
    router.push({
      path: '/generate/image',
      query: { prompt: asset.prompt, model: asset.modelName }
    })
  }
  showDetailDrawer.value = false
}

function handleApplyAsReference() {
  const asset = selectedAsset.value
  if (!asset) return

  if (asset.assetType === 'video') {
    router.push({
      path: '/generate/video',
      query: {
        sourceAssetId: String(asset.id),
        prompt: asset.prompt || '',
        model: asset.modelName || ''
      }
    })
    message.success('已将该视频带入视频工作台继续创作')
  } else {
    router.push({
      path: '/generate/image',
      query: {
        sourceAssetId: String(asset.id),
        prompt: asset.prompt || '',
        model: asset.modelName || ''
      }
    })
    message.success('已将该素材设为图生图参考图')
  }
  showDetailDrawer.value = false
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
    const res = await subtitleApi.list(selectedAsset.value.id)
    subtitles.value = (res.data || []) as SubtitleVO[]
  } catch {
    subtitles.value = []
  }
}

async function handleGenerateSubtitle() {
  if (!selectedAsset.value?.prompt || !selectedAsset.value) {
    message.warning('当前视频资产缺少提示词，暂无法生成字幕')
    return
  }
  subGenerating.value = true
  try {
    await subtitleApi.generate({
      assetId: selectedAsset.value.id,
      projectId: selectedAsset.value.projectId,
      prompt: selectedAsset.value.prompt,
      language: 'zh'
    })
    message.success('字幕生成成功')
    await loadSubtitles()
  } catch (err: any) {
    message.error(err.message || '字幕生成失败')
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
    await subtitleApi.update(editSubtitleId.value, { srtContent: editSubtitleContent.value })
    message.success('字幕已更新')
    showSubEditor.value = false
    await loadSubtitles()
  } catch (err: any) {
    message.error(err.message || '保存失败')
  }
}

async function handleGenerateVoice(id: number) {
  voiceLoading.value = true
  try {
    await subtitleApi.generateVoice(id)
    message.success('配音生成成功')
    await loadSubtitles()
  } catch (err: any) {
    message.error(err.message || '配音失败')
  } finally {
    voiceLoading.value = false
  }
}

async function handleDeleteSubtitle(id: number) {
  try {
    await subtitleApi.delete(id)
    subtitles.value = subtitles.value.filter(item => item.id !== id)
    message.success('字幕已删除')
  } catch (err: any) {
    message.error(err.message || '删除失败')
  }
}

async function handleDeleteAsset() {
  if (!selectedAsset.value) return
  try {
    const id = selectedAsset.value.id
    await assetStore.deleteAsset(id)
    subtitles.value = []
    showDetailDrawer.value = false
    selectedAsset.value = null
    message.success('资产已删除')
  } catch (err: any) {
    message.error(err.message || '删除失败')
  }
}

watch(selectedAsset, () => {
  loadSubtitles()
})

watch(() => projectStore.activeProjectId, () => {
  selectedAsset.value = null
  showDetailDrawer.value = false
  subtitles.value = []
  loadAssets()
})

watch(() => route.query.assetId, () => {
  openAssetFromRoute()
})

onMounted(async () => {
  await loadAssets()
  // 自动刷新
  refreshTimer = window.setInterval(() => { if (document.visibilityState === 'visible') loadAssets() }, 30000)
})

onUnmounted(() => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
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
