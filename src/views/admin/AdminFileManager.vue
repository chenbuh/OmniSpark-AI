<template>
  <div class="admin-files">
    <div class="page-header">
      <h2>文件管理器 (File Manager)</h2>
      <p class="subtitle">浏览、预览和管理上传的生成文件。</p>
    </div>

    <!-- 统计栏 -->
    <n-card class="glass-card stats-bar" :bordered="false">
      <n-space>
        <span class="stat-item">📁 文件总数: <strong>{{ stats.fileCount ?? '-' }}</strong></span>
        <span class="stat-item">💾 总大小: <strong>{{ stats.totalSizeReadable ?? '-' }}</strong></span>
        <span class="stat-item">📂 当前目录: <code>{{ currentPath || '/' }}</code></span>
      </n-space>
    </n-card>

    <!-- 操作栏 -->
    <n-card class="glass-card" :bordered="false" style="margin-top:12px;">
      <div class="toolbar">
        <n-space>
          <n-button size="small" @click="goToParent" :disabled="!currentPath">
            <template #icon><CornerUpLeft /></template>上级目录
          </n-button>
          <n-button size="small" secondary @click="loadFiles('')">根目录</n-button>
        </n-space>
        <n-space>
          <n-button size="small" @click="switchView">
            {{ viewMode === 'grid' ? '📋 列表视图' : '📁 网格视图' }}
          </n-button>
          <n-button size="small" @click="loadFiles(currentPath)">刷新</n-button>
        </n-space>
      </div>

      <!-- 面包屑 -->
      <div class="breadcrumbs" v-if="currentPath">
        <span class="crumb" @click="loadFiles('')">根目录</span>
        <template v-for="(seg, i) in pathSegments" :key="i">
          <span class="crumb-sep">/</span>
          <span class="crumb" @click="loadFiles(pathSegments.slice(0, i + 1).join('/'))">{{ seg }}</span>
        </template>
      </div>

      <!-- 网格视图 -->
      <div v-if="viewMode === 'grid'" class="grid-view">
        <div v-for="item in items" :key="item.relativePath" class="grid-item" @dblclick="openItem(item)">
          <div v-if="isImage(item)" class="grid-thumb" @click="previewFile(item)">
            <img :src="`http://localhost:8080/api/admin/files/preview?path=${encodeURIComponent(item.relativePath)}`" class="thumb-img" loading="lazy" />
          </div>
          <div v-else class="grid-icon-box">
            <span class="grid-icon">{{ item.isDir ? '📁' : getIcon(item.mimeType) }}</span>
          </div>
          <div class="grid-name">{{ item.name }}</div>
          <div class="grid-size">{{ !item.isDir ? formatSize(item.size) : '' }}</div>
          <div class="grid-actions" @click.stop>
            <n-popconfirm v-if="!item.isDir" @positive-click="handleDelete(item)">
              <template #trigger>
                <n-button size="tiny" type="error" tertiary>删除</n-button>
              </template>
              确定删除 {{ item.name }}？
            </n-popconfirm>
          </div>
        </div>
        <div v-if="items.length === 0" class="empty-text">文件夹为空</div>
      </div>

      <!-- 列表视图 -->
      <n-table v-else :single-line="false" class="file-table">
        <thead>
          <tr><th>名称</th><th style="width:100px">大小</th><th style="width:160px">修改时间</th><th style="width:100px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.relativePath" @dblclick="openItem(item)">
            <td><span class="file-icon">{{ item.isDir ? '📁' : getIcon(item.mimeType) }}</span> {{ item.name }}</td>
            <td>{{ item.isDir ? '-' : formatSize(item.size) }}</td>
            <td>{{ new Date(item.lastModified).toLocaleString() }}</td>
            <td>
              <n-space>
                <n-button v-if="isImage(item)" size="tiny" secondary @click="previewFile(item)">预览</n-button>
                <n-popconfirm @positive-click="handleDelete(item)">
                  <template #trigger><n-button size="tiny" type="error" tertiary>删除</n-button></template>
                  确定删除 {{ item.name }}？
                </n-popconfirm>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 图片预览弹窗 -->
    <n-modal v-model:show="showPreview" preset="card" style="width:70vw;max-width:900px;" closable>
      <img v-if="previewUrl" :src="previewUrl" style="width:100%;border-radius:8px;" />
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { CornerUpLeft } from 'lucide-vue-next'
import request from '@/api/request'

const message = useMessage()
const BASE = 'http://localhost:8080'

const items = ref<any[]>([])
const currentPath = ref('')
const stats = ref<any>({})
const viewMode = ref('list')
const showPreview = ref(false)
const previewUrl = ref('')

const pathSegments = computed(() => currentPath.value ? currentPath.value.split('/') : [])

onMounted(() => { loadStats(); loadFiles() })

async function loadFiles(path?: string) {
  currentPath.value = path || ''
  try {
    const res = await request.get('/api/admin/files', { params: { path: currentPath.value } })
    const data = (res as any).data
    items.value = data?.items || []
  } catch { items.value = [] }
}

async function loadStats() {
  try { const res = await request.get('/api/admin/files/stats'); stats.value = (res as any).data || {} } catch {}
}

function goToParent() {
  const p = currentPath.value
  const idx = p.lastIndexOf('/')
  loadFiles(idx > 0 ? p.substring(0, idx) : '')
}

function openItem(item: any) {
  if (item.isDir) { loadFiles(item.relativePath); return }
  if (isImage(item)) previewFile(item)
}

function previewFile(item: any) {
  previewUrl.value = `${BASE}/api/admin/files/preview?path=${encodeURIComponent(item.relativePath)}`
  showPreview.value = true
}

async function handleDelete(item: any) {
  try {
    await request.delete('/api/admin/files', { params: { path: item.relativePath } })
    message.success('已删除')
    await loadFiles(currentPath.value)
  } catch { message.error('删除失败') }
}

function switchView() { viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid' }

function isImage(item: any) {
  const mime = item.mimeType || ''
  return mime.startsWith('image/') && !item.isDir
}

function getIcon(mime: string) {
  if (!mime) return '📄'
  if (mime.startsWith('image/')) return '🖼'
  if (mime.startsWith('video/')) return '🎬'
  if (mime.startsWith('audio/')) return '🎵'
  if (mime.includes('json')) return '📋'
  if (mime.includes('text')) return '📝'
  return '📄'
}

function formatSize(bytes: number) {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<style scoped>
.admin-files { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.stats-bar .stat-item { font-size: 13px; color: #d1d5db; }
.stats-bar .stat-item strong { color: #10b981; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.breadcrumbs { display: flex; align-items: center; gap: 4px; margin-bottom: 12px; padding: 6px 10px; background: rgba(255,255,255,0.02); border-radius: 6px; font-size: 12px; }
.crumb { color: #3b82f6; cursor: pointer; }
.crumb:hover { text-decoration: underline; }
.crumb-sep { color: #4b5563; }

.grid-view { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 12px; }
.grid-item { background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); border-radius: 10px; overflow: hidden; cursor: pointer; transition: transform .2s; text-align: center; }
.grid-item:hover { transform: translateY(-2px); border-color: rgba(255,255,255,0.1); }
.grid-thumb { height: 100px; overflow: hidden; }
.thumb-img { width: 100%; height: 100%; object-fit: cover; }
.grid-icon-box { height: 80px; display: flex; align-items: center; justify-content: center; }
.grid-icon { font-size: 32px; }
.grid-name { font-size: 12px; color: #d1d5db; padding: 4px 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.grid-size { font-size: 10px; color: #6b7280; padding-bottom: 4px; }
.grid-actions { padding: 4px 0 8px; }
.empty-text { grid-column: 1/-1; text-align: center; padding: 40px; color: #6b7280; }

.file-table { background: transparent !important; }
.file-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; font-size: 12px; }
.file-table td { color: #e5e7eb; padding: 6px 8px; font-size: 13px; cursor: pointer; }
.file-icon { margin-right: 6px; }
</style>
