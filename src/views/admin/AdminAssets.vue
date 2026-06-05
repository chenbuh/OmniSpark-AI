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
        </n-descriptions>
        <div class="preview-prompt" v-if="previewAsset.prompt">
          <strong>Prompt:</strong> {{ previewAsset.prompt }}
        </div>
      </div>
      <template #footer>
        <n-button size="small" @click="showPreview = false">关闭</n-button>
        <n-button size="small" type="primary" @click="downloadAsset">下载原文件</n-button>
        <n-button size="small" type="error" @click="handleDelete(previewAsset?.id); showPreview = false">删除</n-button>
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

const message = useMessage()
const loadingAssets = ref(true)
const assets = ref<any[] | null>(null)
const typeFilter = ref<string | null>(null)
const searchText = ref('')
const showPreview = ref(false)
const previewAsset = ref<any>(null)
const page = ref(1)
const pageSize = 12
const total = ref<number | null>(null)
const assetTypeItems = ref<DataDictItem[]>([])
const assetTypeItemsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const totalDisplay = computed(() => total.value == null ? '-' : total.value)

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
    const res = await dictApi.getItems('asset_category')
    if (!Array.isArray((res as any).data)) {
      throw new Error('资产类型待确认')
    }
    const items = (res as any).data
    assetTypeItems.value = items
    assetTypeItemsLoadState.value = 'ready'
  } catch {
    assetTypeItems.value = []
    assetTypeItemsLoadState.value = 'error'
  }
}

// 过滤变化时回到第 1 页
function reload() {
  page.value = 1
  loadAssets()
}

async function loadAssets() {
  loadingAssets.value = true
  try {
    const params: Record<string, any> = { page: page.value, pageSize }
    if (typeFilter.value) params.assetType = typeFilter.value
    if (searchText.value) params.search = searchText.value
    const res = await request.get('/api/admin/assets', { params })
    const data = (res as any).data
    if (!Array.isArray(data.records)) {
      throw new Error('资产数据待确认')
    }
    assets.value = data.records
    total.value = typeof data.total === 'number' ? data.total : 0
  } catch (err: any) {
    assets.value = null
    total.value = null
    message.error(err.message || '加载资产失败')
  } finally {
    loadingAssets.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/api/admin/assets/${id}`)
    message.success('已删除')
    if ((assets.value?.length || 0) === 1 && page.value > 1) page.value--
    await loadAssets()
  } catch (err: any) { message.error(err.message || '删除失败') }
}

function openPreview(asset: any) {
  previewAsset.value = asset
  showPreview.value = true
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
