<template>
  <div class="admin-tasks">
    <div class="page-header">
      <h2>全局任务监管 (Task Supervision)</h2>
      <p class="subtitle">查看和管理所有用户的任务。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <n-space>
          <n-select v-model:value="statusFilter" :options="statusOptions" placeholder="状态" style="width:120px" clearable @update:value="reload" />
          <n-select v-model:value="typeFilter" :options="typeOptions" placeholder="类型" style="width:120px" clearable @update:value="reload" />
          <n-input v-model:value="searchText" placeholder="搜索提示词..." style="width:200px;" clearable @update:value="reload" />
        </n-space>
        <span class="count">共 {{ totalDisplay }} 条</span>
      </div>
      <div v-if="taskMetaLoadState === 'error'" class="status-note">任务状态与类型选项待确认，请稍后重试。</div>

      <div v-if="loadingTasks && tasks === null" class="loading-box">
        <n-spin size="small" />
      </div>

      <n-table v-else :single-line="false" class="admin-table">
        <thead>
          <tr>
            <th style="width:55px">ID</th>
            <th style="width:65px">项目</th>
            <th style="width:55px">类型</th>
            <th>提示词</th>
            <th style="width:75px">模型</th>
            <th style="width:65px">状态</th>
            <th style="width:55px">进度</th>
            <th style="width:110px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks || []" :key="t.id">
            <td><code>#{{ t.id }}</code></td>
            <td>{{ t.projectId }}</td>
            <td><n-tag size="small" :type="taskTypeTagType(t.taskType)" round>{{ taskTypeLabel(t.taskType) }}</n-tag></td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ t.prompt }}</n-ellipsis></td>
            <td><n-tag size="mini" type="info" :bordered="false"><code>{{ t.modelName }}</code></n-tag></td>
            <td><n-tag size="small" :type="statusTagType(t.status)">{{ statusLabel(t.status) }}</n-tag></td>
            <td>{{ formatTaskProgress(t.progress) }}</td>
            <td>
              <n-space :size="4">
                <n-button size="tiny" quaternary @click="showDetail(t)"><template #icon><FileText /></template></n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(t.id)"><template #icon><Trash2 /></template></n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="tasks !== null && tasks.length === 0"><td colspan="8" class="empty-cell">暂无任务</td></tr>
          <tr v-else-if="tasks === null"><td colspan="8" class="empty-cell">任务数据待确认，请稍后重试。</td></tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="(total ?? 0) > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadTasks" />
      </div>
    </n-card>

    <!-- 详情抽屉 -->
    <n-drawer v-model:show="showDrawer" :width="500" placement="right" class="glass-drawer">
      <n-drawer-content :title="'任务 #' + (detail?.id||'')" closable>
        <div v-if="detail" class="drawer-body">
          <div class="ds-card">
            <div class="ds-row"><span class="ds-lbl">状态</span>
              <n-tag :type="statusTagType(detail.status)" round>{{ statusLabel(detail.status) }}</n-tag>
            </div>
            <div class="ds-row"><span class="ds-lbl">进度</span><span>{{ formatTaskProgress(detail.progress) }}</span></div>
            <div class="ds-row" v-if="detail.progressText"><span class="ds-lbl">进度描述</span><span class="ds-val">{{ detail.progressText }}</span></div>
            <div class="ds-row"><span class="ds-lbl">类型</span><span>{{ taskTypeLabel(detail.taskType) }}</span></div>
            <div class="ds-row"><span class="ds-lbl">项目</span><span>项目 #{{ detail.projectId }}</span></div>
          </div>
          <div class="ds-card">
            <div class="ds-row"><span class="ds-lbl">模型</span><n-tag size="small" type="info"><code>{{ detail.modelName }}</code></n-tag></div>
            <div class="ds-row"><span class="ds-lbl">创建</span><span>{{ String(detail.createdAt||'').replace('T',' ').substring(0,19) }}</span></div>
          </div>
          <div class="ds-section"><h4 class="ds-title">提示词</h4><div class="ds-code">{{ detail.prompt }}</div></div>
          <div class="ds-section" v-if="detail.negativePrompt"><h4 class="ds-title">负向提示词</h4><div class="ds-code">{{ detail.negativePrompt }}</div></div>
          <div class="ds-section" v-if="detail.requestJson"><h4 class="ds-title">请求参数</h4><n-code :code="formatJson(detail.requestJson)" language="json" /></div>
          <div class="ds-section" v-if="detail.responseJson"><h4 class="ds-title">响应数据</h4><n-code :code="formatJson(detail.responseJson)" language="json" /></div>
          <div class="ds-section" v-if="detail.errorMessage">
            <h4 class="ds-title" style="color:#ef4444;">错误信息</h4>
            <n-alert type="error" :bordered="false" style="font-size:12px;">{{ detail.errorMessage }}</n-alert>
          </div>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { FileText, Trash2 } from 'lucide-vue-next'
import request from '@/api/request'

const message = useMessage()
const loadingTasks = ref(true)
const tasks = ref<any[] | null>(null)
const statusFilter = ref<string | null>(null)
const typeFilter = ref<string | null>(null)
const searchText = ref('')
const showDrawer = ref(false)
const detail = ref<any>(null)
const page = ref(1)
const pageSize = 10
const total = ref<number | null>(null)
const taskStatuses = ref<string[]>([])
const taskTypes = ref<string[]>([])
const taskMetaLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const totalDisplay = computed(() => total.value == null ? '-' : total.value)

const statusOptions = computed(() => taskStatuses.value.map(value => ({
  label: statusLabel(value),
  value
})))
const typeOptions = computed(() => taskTypes.value.map(value => ({
  label: taskTypeLabel(value),
  value
})))

onMounted(async () => {
  await loadTaskMeta()
  await loadTasks()
})

const statusLabel = (status: string) => status === 'pending' ? '排队中' : status === 'running' ? '运行中' : status === 'success' ? '成功' : status === 'failed' ? '失败' : (status || '未知')
const taskTypeLabel = (taskType: string) => taskType === 'image' ? '生图' : taskType === 'video' ? '视频' : (taskType || '未知类型')
const statusTagType = (status: string) => status === 'success' ? 'success' : status === 'failed' ? 'error' : 'warning'
const taskTypeTagType = (taskType: string) => taskType === 'image' ? 'success' : taskType === 'video' ? 'warning' : 'default'
const formatTaskProgress = (progress: unknown) => {
  if (progress == null || progress === '') return '-'
  const normalized = Number(progress)
  return Number.isNaN(normalized) ? '-' : `${Math.max(0, Math.min(100, normalized))}%`
}

async function loadTaskMeta() {
  taskMetaLoadState.value = 'loading'
  try {
    const res = await request.get('/api/admin/tasks/meta')
    const data = (res as any).data || {}
    if (!Array.isArray(data.statuses) || !Array.isArray(data.taskTypes)) {
      throw new Error('任务元数据待确认')
    }
    taskStatuses.value = data.statuses
    taskTypes.value = data.taskTypes
    taskMetaLoadState.value = 'ready'
  } catch {
    taskStatuses.value = []
    taskTypes.value = []
    taskMetaLoadState.value = 'error'
  }
}

// 过滤条件变化时回到第 1 页再查
function reload() {
  page.value = 1
  loadTasks()
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const params: Record<string, any> = { page: page.value, pageSize }
    if (statusFilter.value) params.status = statusFilter.value
    if (typeFilter.value) params.taskType = typeFilter.value
    if (searchText.value) params.search = searchText.value
    const res = await request.get('/api/admin/tasks', { params })
    const data = (res as any).data || {}
    if (!Array.isArray(data.records)) {
      tasks.value = null
      total.value = null
      return
    }
    tasks.value = data.records
    total.value = typeof data.total === 'number' ? data.total : 0
    mergeTaskMetaFromRecords(data.records)
  } catch (err: any) {
    tasks.value = null
    total.value = null
    message.error(err.message || '加载任务失败')
  } finally {
    loadingTasks.value = false
  }
}

function mergeTaskMetaFromRecords(records: any[]) {
  const statusSet = new Set(taskStatuses.value)
  const typeSet = new Set(taskTypes.value)
  records.forEach(record => {
    const status = typeof record?.status === 'string' ? record.status.trim() : ''
    const taskType = typeof record?.taskType === 'string' ? record.taskType.trim() : ''
    if (status) statusSet.add(status)
    if (taskType) typeSet.add(taskType)
  })
  taskStatuses.value = Array.from(statusSet).sort((left, right) => taskStatusOrder(left) - taskStatusOrder(right) || left.localeCompare(right))
  taskTypes.value = Array.from(typeSet).sort((left, right) => taskTypeOrder(left) - taskTypeOrder(right) || left.localeCompare(right))
}

function taskStatusOrder(status: string) {
  if (status === 'pending') return 0
  if (status === 'running') return 1
  if (status === 'success') return 2
  if (status === 'failed') return 3
  return 99
}

function taskTypeOrder(taskType: string) {
  if (taskType === 'image') return 0
  if (taskType === 'video') return 1
  return 99
}

async function handleDelete(id: number) {
  try {
    await request.delete(`/api/admin/tasks/${id}`)
    message.success('已删除')
    // 删除当前页最后一条时回退一页,避免停留空页
    if ((tasks.value?.length || 0) === 1 && page.value > 1) page.value--
    await loadTasks()
  } catch (err: any) { message.error(err.message || '删除失败') }
}

function showDetail(t: any) {
  detail.value = t
  showDrawer.value = true
}

function formatJson(s: string) {
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s }
}
</script>

<style scoped>
.admin-tasks { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.count { font-size: 12px; color: var(--text-muted); }
.status-note { margin-bottom: 12px; font-size: 12px; color: #f59e0b; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(128,128,128,0.02) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary); padding: 8px; font-size: 12px; }
.empty-cell { text-align: center; padding: 30px !important; color: var(--text-muted); }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }

.drawer-body { display: flex; flex-direction: column; gap: 16px; }
.ds-card { display: flex; flex-direction: column; gap: 8px; padding: 12px; background: rgba(128,128,128,0.02); border-radius: 10px; }
.ds-row { display: flex; justify-content: space-between; align-items: center; font-size: 13px; }
.ds-lbl { color: var(--text-muted); font-size: 12px; }
.ds-val { color: #10b981; }
.ds-section { display: flex; flex-direction: column; gap: 8px; }
.ds-title { font-size: 13px; font-weight: 600; color: var(--text-primary); margin: 0; padding-bottom: 4px; border-bottom: 1px solid var(--border-color); }
.ds-code { font-size: 12px; color: var(--text-secondary); line-height: 1.5; padding: 10px; background: rgba(128,128,128,0.04); border-radius: 8px; word-break: break-all; }
.glass-drawer { background: rgba(11,15,23,0.95) !important; backdrop-filter: blur(20px); }
</style>
