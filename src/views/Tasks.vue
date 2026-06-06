<template>
  <div class="tasks-container">
    <div class="page-header">
      <h2>任务中心 (Task Queue)</h2>
      <p class="subtitle">跟踪并管理当前空间下所有的生图与生视频异步渲染任务队列进度。</p>
    </div>

    <!-- 过滤与统计条 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-bar">
        <n-space align="center" :size="16" wrap>
          <n-radio-group v-model:value="statusFilter" size="small">
            <n-radio-button value="all">全部 ({{ taskCount.all }})</n-radio-button>
            <n-radio-button value="running">进行中 ({{ taskCount.running }})</n-radio-button>
            <n-radio-button value="success">成功 ({{ taskCount.success }})</n-radio-button>
            <n-radio-button value="failed">失败 ({{ taskCount.failed }})</n-radio-button>
          </n-radio-group>
          <n-select v-model:value="typeFilter" :options="typeOptions" style="width:110px;" size="small" />
          <n-input v-model:value="searchQuery" placeholder="搜索提示词..." style="width: 200px;" size="small" clearable>
            <template #prefix><Search class="search-icon" /></template>
          </n-input>
        </n-space>
        <n-space :size="8">
          <n-button size="small" secondary :loading="loading" @click="handleRefresh">
            <template #icon><RefreshCw /></template>刷新
          </n-button>
          <n-button size="small" type="error" tertiary @click="handleClearAll">清空已完成</n-button>
        </n-space>
      </div>
    </n-card>

    <!-- 加载骨架 -->
    <SkeletonCard v-if="loading" type="table" :rows="5" :cols="6" />

    <!-- 表格 -->
    <n-card v-else class="glass-card table-card" :bordered="false" style="margin-top:20px;">
      <n-table :single-line="false" class="tasks-table">
        <thead>
          <tr>
            <th style="width:40px;"><n-checkbox :checked="allSelected" @update:checked="toggleAll" /></th>
            <th style="width:80px;">ID</th>
            <th style="width:80px;">类型</th>
            <th style="width:140px;">提供商/模型</th>
            <th>提示词</th>
            <th style="width:150px;">进度</th>
            <th style="width:100px;">时间</th>
            <th style="width:100px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="task in filteredTasks" :key="task.id" :class="{ 'row-selected': selectedIds.has(task.id) }">
            <td><n-checkbox :checked="selectedIds.has(task.id)" @update:checked="toggleOne(task.id)" /></td>
            <td><code>#{{ String(task.id).slice(-6) }}</code></td>
            <td>
              <n-tag :type="taskTypeTagType(task.taskType)" size="tiny" round>
                <template #icon><Image v-if="task.taskType==='image'" class="t-icon" /><Video v-else-if="task.taskType==='video'" class="t-icon" /></template>
                {{ taskTypeLabel(task.taskType) }}
              </n-tag>
            </td>
            <td>
              <div class="model-cell">
                <span class="provider-lbl">{{ getProviderName(task.providerId) }}</span>
                <n-tag size="mini" type="info" :bordered="false"><code>{{ getTaskDisplayModelName(task) }}</code></n-tag>
              </div>
            </td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true" style="max-width:380px;">{{ getTaskDisplayPrompt(task) }}</n-ellipsis></td>
            <td>
              <div class="progress-cell">
                <div class="pct-row">
                  <span class="pct-num" :class="task.status">{{ formatTaskProgress(task.progress) }}</span>
                  <span class="status-lbl">{{ statusLabel(task.status) }}</span>
                </div>
                <n-progress v-if="task.progress != null" type="line" :percentage="task.progress" :status="task.status==='success'?'success':task.status==='failed'?'error':'warning'" :show-indicator="false" :height="4" />
                <span v-else class="step-lbl">进度待确认</span>
                <span class="step-lbl" v-if="task.status==='running' && task.progressText">{{ task.progressText }}</span>
              </div>
            </td>
            <td><span class="date-lbl">{{ String(task.createdAt||'').substring(5,16) }}</span></td>
            <td>
              <n-space :size="4">
                <n-button size="tiny" quaternary @click="handleOpenDetail(task)"><template #icon><FileText /></template></n-button>
                <n-button v-if="task.status==='success'" size="tiny" quaternary @click="handleViewAsset(task)"><template #icon><Eye /></template></n-button>
                <n-button v-if="task.status==='failed'||task.status==='success'" size="tiny" warning quaternary @click="handleRetry(task.id)"><template #icon><RotateCw /></template></n-button>
                <n-button size="tiny" error tertiary @click="handleDelete(task.id)"><template #icon><Trash2 /></template></n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="tasksReady && filteredTasks.length===0">
            <td colspan="8" class="empty-row"><ClipboardList class="empty-icon" /><span>暂无任务</span></td>
          </tr>
          <tr v-else-if="!tasksReady">
            <td colspan="8" class="empty-row"><ClipboardList class="empty-icon" /><span>任务数据待确认</span></td>
          </tr>
        </tbody>
      </n-table>
      <div class="batch-bar" v-if="selectedIds.size>0">
        <span>已选 {{ selectedIds.size }} 项</span>
        <n-button size="tiny" type="error" tertiary @click="handleBatchDelete">批量删除</n-button>
        <n-button size="tiny" @click="selectedIds.clear()">取消选择</n-button>
      </div>
    </n-card>

    <!-- 详情抽屉 -->
    <n-drawer v-model:show="showDetailDrawer" :width="520" placement="right" class="glass-drawer">
      <n-drawer-content :title="'任务 #'+(selectedTask?.id?.toString().slice(-6)||'')" closable>
        <div class="drawer-inner" v-if="selectedTask">
          <div class="detail-status-bar">
            <n-tag :type="selectedTask.status==='success'?'success':selectedTask.status==='failed'?'error':'warning'" size="medium" round>{{ statusLabel(selectedTask.status) }}</n-tag>
            <n-progress v-if="selectedTask.progress != null" type="circle" :percentage="selectedTask.progress" :status="selectedTask.status==='success'?'success':selectedTask.status==='failed'?'error':'warning'" :stroke-width="5" :width="70">
              <template #default><span class="dr-pct">{{ formatTaskProgress(selectedTask.progress) }}</span></template>
            </n-progress>
            <div v-else class="detail-progress-pending">进度待确认</div>
          </div>
          <div class="detail-section">
            <h4 class="section-title">基础信息</h4>
            <div class="info-grid">
              <div class="info-item"><span class="info-label">任务 ID</span><span class="info-val"><code>#{{ selectedTask.id }}</code></span></div>
              <div class="info-item"><span class="info-label">类型</span><n-tag :type="taskTypeTagType(selectedTask.taskType)" size="small" round>{{ taskTypeLabel(selectedTask.taskType) }}</n-tag></div>
              <div class="info-item"><span class="info-label">提供商</span><span class="info-val">{{ getProviderName(selectedTask.providerId) }}</span></div>
              <div class="info-item"><span class="info-label">模型</span><n-tag size="small" type="info"><code>{{ getTaskDisplayModelName(selectedTask) }}</code></n-tag></div>
              <div class="info-item"><span class="info-label">创建</span><span class="info-val">{{ String(selectedTask.createdAt||'').replace('T',' ').substring(0,19) }}</span></div>
              <div class="info-item" v-if="selectedTask.progressText"><span class="info-label">进度</span><span class="info-val dr-progress-text">{{ selectedTask.progressText }}</span></div>
              <div class="info-item" v-if="selectedTask.finishedAt"><span class="info-label">完成</span><span class="info-val">{{ String(selectedTask.finishedAt||'').replace('T',' ').substring(0,19) }}</span></div>
            </div>
          </div>
          <div class="detail-section">
            <h4 class="section-title">提示词</h4>
            <div class="prompt-box">
              <div class="prompt-block"><span class="prompt-label">Prompt</span><p class="prompt-text">{{ getTaskDisplayPrompt(selectedTask) }}</p></div>
              <div class="prompt-block" v-if="getTaskDisplayNegativePrompt(selectedTask)"><span class="prompt-label negative">Negative</span><p class="prompt-text">{{ getTaskDisplayNegativePrompt(selectedTask) }}</p></div>
            </div>
          </div>
          <div class="detail-section" v-if="selectedTask.requestJson">
            <h4 class="section-title">请求参数</h4>
            <n-code :code="formatJson(selectedTask.requestJson)" language="json" />
          </div>
          <div class="detail-section" v-if="selectedTask.responseJson">
            <h4 class="section-title">响应数据</h4>
            <n-code :code="formatJson(selectedTask.responseJson)" language="json" />
          </div>
          <div class="detail-section" v-if="selectedTask.status==='failed'&&selectedTask.errorMessage">
            <h4 class="section-title error-title">错误原因</h4>
            <n-alert type="error" :bordered="false">{{ selectedTask.errorMessage }}</n-alert>
          </div>
          <div class="detail-section" v-if="selectedTask.status==='success'&&relatedAssets.length>0">
            <h4 class="section-title">生成资产 ({{ relatedAssets.length }})</h4>
            <div class="related-assets-row">
              <div v-for="asset in relatedAssets" :key="asset.id" class="related-asset-thumb" @click="handlePreviewAsset(asset)">
                <div class="thumb-type-badge" v-if="asset.assetType==='video'"><Video class="badge-icon-sm" /></div>
                <img :src="asset.thumbUrl" :alt="asset.fileName || '任务关联资产缩略图'" class="thumb-img-sm" />
                <div class="thumb-overlay"><Eye class="overlay-icon-sm" /></div>
              </div>
            </div>
          </div>
          <div class="detail-actions">
            <n-button secondary block @click="handleCopyParams(selectedTask)"><template #icon><Copy /></template>复制参数</n-button>
            <n-button v-if="selectedTask.status==='failed'||selectedTask.status==='success'" type="warning" secondary block @click="handleRetryFromDetail"><template #icon><RotateCw /></template>重试</n-button>
            <n-button type="error" tertiary block @click="handleDeleteFromDetail"><template #icon><Trash2 /></template>删除</n-button>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useProjectStore } from '@/store/project'
import { useTaskStore, type GenerationTask } from '@/store/task'
import { useModelProviderStore } from '@/store/provider'
import { useAssetStore, type Asset } from '@/store/asset'
import SkeletonCard from '@/components/SkeletonCard.vue'
import { Search, ClipboardList, Image, Video, Copy, Eye, RotateCw, FileText, Trash2, RefreshCw } from 'lucide-vue-next'

const router = useRouter()
const message = useMessage()
const projectStore = useProjectStore()
const taskStore = useTaskStore()
const providerStore = useModelProviderStore()
const assetStore = useAssetStore()

const loading = ref(true)
const statusFilter = ref('all')
const typeFilter = ref<string | null>(null)
const searchQuery = ref('')
const showDetailDrawer = ref(false)
const selectedTask = ref<GenerationTask | null>(null)
const selectedIds = ref(new Set<number>())
const tasksReady = ref(false)
const providerLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const assetLoadState = ref<'loading' | 'ready' | 'error'>('loading')
let autoTimer: ReturnType<typeof setInterval> | null = null
let isRefreshing = false

const allSelected = computed(() => filteredTasks.value.length > 0 && filteredTasks.value.every(t => selectedIds.value.has(t.id)))
const typeOptions = computed(() => {
  const values = new Set(taskStore.getTasksByProject(projectStore.activeProjectId).map(task => task.taskType).filter(Boolean))
  return [
    { label: '全部类型', value: '' },
    ...Array.from(values).map(value => ({
      label: taskTypeLabel(value),
      value
    }))
  ]
})

function toggleAll(val: boolean) {
  if (val) filteredTasks.value.forEach(t => selectedIds.value.add(t.id))
  else selectedIds.value.clear()
}
function toggleOne(id: number) {
  if (selectedIds.value.has(id)) selectedIds.value.delete(id)
  else selectedIds.value.add(id)
}

const getProviderName = (id: number) => {
  const provider = providerStore.providers.find(p => p.id === id)
  if (provider?.name) {
    return provider.name
  }
  return providerLoadState.value === 'error' ? '待确认' : '未知'
}
const statusLabel = (s: string) => s === 'pending' ? '排队中' : s === 'running' ? '渲染中' : s === 'success' ? '成功' : s === 'failed' ? '失败' : (s || '未知')
const taskTypeLabel = (taskType: string) => taskType === 'image' ? '生图' : taskType === 'video' ? '视频' : (taskType || '未知类型')
const taskTypeTagType = (taskType: string) => taskType === 'image' ? 'success' : taskType === 'video' ? 'warning' : 'default'
const formatTaskProgress = (progress?: number) => typeof progress === 'number' ? `${progress}%` : '-'

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

function getTaskDisplayNegativePrompt(task?: GenerationTask | null) {
  const payload = tryParseTaskRequestJson(task)
  return normalizeTaskField(payload?.negativePrompt) || task?.negativePrompt || ''
}

function getTaskDisplayModelName(task?: GenerationTask | null) {
  const payload = tryParseTaskRequestJson(task)
  return normalizeTaskField(payload?.modelName) || task?.modelName || ''
}

function syncSelectedTaskFromStore() {
  const currentTask = selectedTask.value
  if (!currentTask) {
    return
  }
  const refreshed = taskStore.tasks.find(task => task.id === currentTask.id) || null
  selectedTask.value = refreshed
  if (!refreshed) {
    showDetailDrawer.value = false
  }
}

async function refresh() {
  if (isRefreshing) return
  isRefreshing = true
  try {
    const activeProjectId = projectStore.activeProjectId
    if (!activeProjectId) {
      tasksReady.value = true
      providerLoadState.value = 'ready'
      assetLoadState.value = 'ready'
      selectedIds.value.clear()
      selectedTask.value = null
      showDetailDrawer.value = false
      return
    }
    const [tasksResult, providersResult, assetsResult] = await Promise.allSettled([
      taskStore.refresh({ projectId: activeProjectId }),
      providerStore.refresh(activeProjectId),
      assetStore.refresh({ projectId: activeProjectId })
    ])
    if (tasksResult.status !== 'fulfilled') {
      throw tasksResult.reason
    }
    tasksReady.value = true
    providerLoadState.value = providersResult.status === 'fulfilled' ? 'ready' : 'error'
    assetLoadState.value = assetsResult.status === 'fulfilled' ? 'ready' : 'error'
    syncSelectedTaskFromStore()
  } catch (err: unknown) {
    tasksReady.value = false
    message.error(err instanceof Error && err.message ? err.message : '加载任务失败')
  } finally {
    isRefreshing = false
    loading.value = false
  }
}

const taskCount = computed(() => {
  if (!tasksReady.value) {
    return { all: '-', running: '-', success: '-', failed: '-' }
  }
  const all = taskStore.getTasksByProject(projectStore.activeProjectId)
  return { all: all.length, running: all.filter(t => t.status==='running'||t.status==='pending').length, success: all.filter(t => t.status==='success').length, failed: all.filter(t => t.status==='failed').length }
})

const filteredTasks = computed<GenerationTask[]>(() => {
  let list = taskStore.getTasksByProject(projectStore.activeProjectId)
  if (statusFilter.value==='running') list = list.filter(t => t.status==='running'||t.status==='pending')
  else if (statusFilter.value==='success') list = list.filter(t => t.status==='success')
  else if (statusFilter.value==='failed') list = list.filter(t => t.status==='failed')
  if (typeFilter.value) list = list.filter(t => t.taskType === typeFilter.value)
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    list = list.filter(t => getTaskDisplayPrompt(t).toLowerCase().includes(q))
  }
  return list
})

function handleRefresh() { refresh() }

onMounted(() => {
  refresh()
  autoTimer = setInterval(() => {
    if (document.visibilityState === 'visible') refresh()
  }, 10000)
})
onBeforeUnmount(() => { if (autoTimer) clearInterval(autoTimer) })

watch(() => projectStore.activeProjectId, () => {
  selectedIds.value.clear()
  selectedTask.value = null
  showDetailDrawer.value = false
  void refresh()
})

function buildTaskParameterCopyText(task: GenerationTask) {
  if (tryParseTaskRequestJson(task)) {
    return formatJson(task.requestJson)
  }
  if (task.requestJson) {
    return task.requestJson
  }
  return JSON.stringify({
    projectId: task.projectId,
    providerId: task.providerId,
    taskType: task.taskType,
    prompt: task.prompt,
    negativePrompt: task.negativePrompt || '',
    modelName: task.modelName,
    options: task.options || {},
    resultAssetId: task.resultAssetId || null
  }, null, 2)
}

const handleCopyParams = async (task: GenerationTask) => {
  try {
    await navigator.clipboard.writeText(buildTaskParameterCopyText(task))
    const payload = tryParseTaskRequestJson(task)
    message.success(
      payload
        ? '真实请求参数已复制'
        : task.requestJson
          ? '已复制任务原始请求记录'
          : '已复制当前任务已记录的参数'
    )
  } catch {
    message.error('复制失败，请稍后再试')
  }
}

const handleViewAsset = (task: GenerationTask) => {
  const asset = findTaskAssets(task)[0]
  if (asset) {
    router.push({ path: '/assets', query: { assetId: String(asset.id) } })
    return
  }
  message.error('关联资产待确认')
}

function hasCurrentProjectTask(taskId: number) {
  return taskStore.getTasksByProject(projectStore.activeProjectId).some(task => task.id === taskId)
}

function ensureCurrentProjectTasksRemoved(taskIds: number[]) {
  const remaining = taskIds.filter(id => hasCurrentProjectTask(id))
  if (remaining.length > 0) {
    throw new Error('任务删除结果待确认')
  }
}

function currentProjectTaskCount() {
  return taskStore.getTasksByProject(projectStore.activeProjectId).length
}

function ensureCurrentProjectTaskCount(expectedCount: number, errorMessage: string) {
  if (currentProjectTaskCount() !== expectedCount) {
    throw new Error(errorMessage)
  }
}

const handleRetry = async (id: number) => {
  try {
    const retried = await taskStore.retryTask(id)
    await refresh()
    if (!hasCurrentProjectTask(retried.id)) {
      throw new Error('任务重试结果待确认')
    }
    if (retried.status === 'failed') {
      message.error(retried.errorMessage || '任务重试后执行失败')
      return false
    }
    message.success(retried.status === 'success' ? '任务已重试并完成' : '任务已重新提交')
    return true
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '任务重试失败')
    return false
  }
}

const handleDelete = async (id: number) => {
  try {
    const previousCount = currentProjectTaskCount()
    await taskStore.deleteTask(id)
    selectedIds.value.delete(id)
    await refresh()
    ensureCurrentProjectTasksRemoved([id])
    ensureCurrentProjectTaskCount(Math.max(0, previousCount - 1), '任务删除结果待确认')
    if (selectedTask.value?.id === id) {
      selectedTask.value = null
    }
    message.success('已删除')
    return true
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '删除失败')
    return false
  }
}

function findTaskAssets(task: { id: number; resultAssetId?: number }) {
  if (assetLoadState.value !== 'ready') {
    return []
  }
  const linkedAssets = assetStore.assets.filter(asset => asset.taskId === task.id)
  if (linkedAssets.length > 0) {
    return linkedAssets
  }
  if (task.resultAssetId) {
    const exact = assetStore.assets.find(asset => asset.id === task.resultAssetId && asset.taskId === task.id)
    if (exact) {
      return [exact]
    }
  }
  return []
}

const relatedAssets = computed<Asset[]>(() => {
  const currentTask = selectedTask.value
  if (!currentTask) return []
  return findTaskAssets(currentTask)
})

const formatJson = (value?: string) => {
  const source = value || ''
  try { return JSON.stringify(JSON.parse(source), null, 2) } catch { return source }
}

const handleOpenDetail = (task: GenerationTask) => { selectedTask.value = task; showDetailDrawer.value = true }
const handleRetryFromDetail = async () => {
  if (!selectedTask.value) return
  const ok = await handleRetry(selectedTask.value.id)
  if (ok) {
    showDetailDrawer.value = false
  }
}
const handleDeleteFromDetail = async () => {
  if (!selectedTask.value) return
  const ok = await handleDelete(selectedTask.value.id)
  if (ok) {
    showDetailDrawer.value = false
    selectedTask.value = null
  }
}
const handlePreviewAsset = (asset: Asset) => { showDetailDrawer.value = false; router.push({ path: '/assets', query: { assetId: String(asset.id) } }) }

const handleBatchDelete = async () => {
  const ids = [...selectedIds.value]
  if (ids.length === 0) return
  try {
    const previousCount = currentProjectTaskCount()
    for (const id of ids) {
      await taskStore.deleteTask(id)
    }
    selectedIds.value.clear()
    await refresh()
    ensureCurrentProjectTasksRemoved(ids)
    ensureCurrentProjectTaskCount(Math.max(0, previousCount - ids.length), '批量删除结果待确认')
    if (selectedTask.value && ids.includes(selectedTask.value.id)) {
      selectedTask.value = null
    }
    message.success(`已删除 ${ids.length} 项`)
  } catch (err: unknown) {
    await refresh()
    message.error(err instanceof Error && err.message ? err.message : '批量删除失败')
  }
}

const handleClearAll = async () => {
  const ids = filteredTasks.value.filter(t => t.status==='success'||t.status==='failed').map(t => t.id)
  if (ids.length === 0) {
    message.info('当前没有可清空的已完成任务')
    return
  }
  try {
    const previousCount = currentProjectTaskCount()
    for (const id of ids) {
      await taskStore.deleteTask(id)
    }
    selectedIds.value.clear()
    await refresh()
    ensureCurrentProjectTasksRemoved(ids)
    ensureCurrentProjectTaskCount(Math.max(0, previousCount - ids.length), '清空结果待确认')
    if (selectedTask.value && ids.includes(selectedTask.value.id)) {
      selectedTask.value = null
    }
    message.success('已清空')
  } catch (err: unknown) {
    await refresh()
    message.error(err instanceof Error && err.message ? err.message : '清空失败')
  }
}
</script>

<style scoped>
.tasks-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.search-icon { width: 14px; height: 14px; color: var(--text-muted); }
.t-icon { width: 12px; height: 12px; margin-right: 2px; }

.tasks-table { background: transparent !important; }
.tasks-table th { background: rgba(128,128,128,0.02) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px; }
.tasks-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary); padding: 12px 8px; font-size: 13px; }
.row-selected { background: rgba(16,185,129,0.04); }
.model-cell { display: flex; flex-direction: column; gap: 3px; }
.provider-lbl { font-size: 11px; color: var(--text-muted); }
.progress-cell { display: flex; flex-direction: column; gap: 4px; }
.pct-row { display: flex; justify-content: space-between; font-size: 11px; }
.pct-num { font-weight: 700; color: #f59e0b; }
.pct-num.success { color: #10b981; }
.pct-num.failed { color: #ef4444; }
.status-lbl { color: var(--text-muted); font-size: 11px; }
.step-lbl { font-size: 10px; color: #10b981; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.date-lbl { font-size: 11px; color: var(--text-muted); }
.empty-row { text-align: center !important; padding: 40px 0 !important; color: var(--text-muted); }
.empty-row .empty-icon { width: 36px; height: 36px; margin: 0 auto 8px; opacity: 0.3; display: block; }
.batch-bar { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-top: 1px solid var(--border-color); font-size: 12px; color: var(--text-muted); }

.drawer-inner { display: flex; flex-direction: column; gap: 20px; }
.detail-status-bar { display: flex; align-items: center; justify-content: space-between; padding: 16px; background: rgba(128,128,128,0.02); border-radius: 12px; border: 1px solid var(--border-color); }
.dr-pct { font-size: 14px; font-weight: 700; color: var(--text-primary); }
.detail-section { display: flex; flex-direction: column; gap: 10px; }
.section-title { font-size: 14px; font-weight: 600; color: var(--text-primary); margin: 0; padding-bottom: 6px; border-bottom: 1px solid var(--border-color); }
.section-title.error-title { color: #ef4444; }
.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.info-item { display: flex; flex-direction: column; gap: 4px; }
.info-label { font-size: 11px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.3px; }
.info-val { font-size: 13px; color: var(--text-secondary); }
.dr-progress-text { color: #10b981; font-size: 12px; }
.prompt-box { display: flex; flex-direction: column; gap: 12px; }
.prompt-block { display: flex; flex-direction: column; gap: 6px; }
.prompt-label { font-size: 11px; color: #10b981; font-weight: 600; text-transform: uppercase; }
.prompt-label.negative { color: #ef4444; }
.prompt-text { font-size: 13px; color: var(--text-secondary); line-height: 1.6; margin: 0; padding: 10px 12px; background: rgba(128,128,128,0.04); border-radius: 8px; border: 1px solid var(--border-light); }
.related-assets-row { display: flex; flex-wrap: wrap; gap: 10px; }
.related-asset-thumb { position: relative; width: 80px; height: 80px; border-radius: 8px; overflow: hidden; cursor: pointer; border: 1px solid var(--border-color); transition: border-color .3s; }
.related-asset-thumb:hover { border-color: #10b981; }
.thumb-type-badge { position: absolute; top: 4px; left: 4px; z-index: 2; }
.badge-icon-sm { width: 14px; height: 14px; color: #fff; filter: drop-shadow(0 0 2px rgba(0,0,0,0.8)); }
.thumb-img-sm { width: 100%; height: 100%; object-fit: cover; }
.thumb-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity .3s; }
.related-asset-thumb:hover .thumb-overlay { opacity: 1; }
.overlay-icon-sm { width: 20px; height: 20px; color: #fff; }
.detail-actions { display: flex; flex-direction: column; gap: 8px; padding-top: 12px; border-top: 1px solid var(--border-color); }
.detail-progress-pending { width: 70px; text-align: center; font-size: 12px; color: var(--text-muted); }
.glass-drawer { background: rgba(11,15,23,0.95) !important; backdrop-filter: blur(20px); }
</style>
