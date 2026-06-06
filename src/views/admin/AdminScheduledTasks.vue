<template>
  <div class="admin-scheduler">
    <div class="page-header">
      <h2>定时任务调度 (Scheduled Tasks)</h2>
      <p class="subtitle">自定义管理系统定时任务，目前仅开放真实已实现的数据清理任务。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <span class="count">共 {{ tasksCountDisplay }} 个任务</span>
        <n-button type="primary" size="small" @click="openCreate">
          <template #icon><Plus /></template>新建任务
        </n-button>
      </div>
      <div v-if="tasksLoadState === 'error'" class="status-note">定时任务数据待确认，请稍后重试。</div>

      <div v-if="loadingTasks && tasks === null" class="loading-box">
        <n-spin size="small" />
      </div>
      <n-table v-else :single-line="false" class="admin-table">
        <thead>
          <tr>
            <th style="width:50px">ID</th>
            <th>任务名称</th>
            <th>描述</th>
            <th style="width:110px">类型</th>
            <th style="width:110px">Cron</th>
            <th style="width:60px">启用</th>
            <th style="width:150px">上次运行</th>
            <th style="width:70px">结果</th>
            <th style="width:180px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks || []" :key="t.id">
            <td><code>#{{ t.id }}</code></td>
            <td>{{ t.name }}</td>
            <td><n-ellipsis :line-clamp="1">{{ t.description }}</n-ellipsis></td>
            <td><n-tag size="small">{{ t.taskType }}</n-tag></td>
            <td><n-tag size="small" type="info">{{ t.cron }}</n-tag></td>
            <td>
              <n-tag v-if="normalizeBinaryStatus(t.enabled) === null" size="small" type="warning">状态待确认</n-tag>
              <n-switch v-else :value="normalizeBinaryStatus(t.enabled) === true" @update:value="handleToggle(t.id)" />
            </td>
            <td style="font-size:12px;">{{ t.lastRunAt ? t.lastRunAt.substring(0,19).replace('T',' ') : '-' }}</td>
            <td><n-tag size="small" :type="t.lastStatus === 'success' ? 'success' : t.lastStatus === 'failed' ? 'error' : 'default'">{{ t.lastStatus || '-' }}</n-tag></td>
            <td>
              <n-space>
                <n-button size="tiny" secondary @click="handleRunNow(t.id)" :loading="runningId === t.id" title="立即执行">
                  <template #icon><Play /></template>
                </n-button>
                <n-button size="tiny" secondary @click="openEdit(t)" title="编辑">
                  <template #icon><Edit3 /></template>
                </n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(t.id)" title="删除">
                  <template #icon><Trash2 /></template>
                </n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="tasks !== null && tasks.length === 0">
            <td colspan="9" class="empty-cell">暂无定时任务</td>
          </tr>
          <tr v-else-if="tasks === null">
            <td colspan="9" class="empty-cell">定时任务数据待确认，请稍后重试。</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 新建/编辑弹窗 -->
    <n-modal v-model:show="showEditor" preset="card" :title="editingId ? '编辑定时任务' : '新建定时任务'" style="width:560px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="任务名称" required>
          <n-input v-model:value="form.name" placeholder="例如：每日凌晨清理旧数据" :maxlength="60" show-count />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" placeholder="描述这个任务的作用" />
        </n-form-item>
        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="任务类型" required>
              <n-select v-model:value="form.taskType" :options="typeOptions" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="Cron 表达式" required>
              <n-input v-model:value="form.cron" placeholder="如 0 0 3 * * ? (每天3点)" />
            </n-form-item>
          </n-col>
        </n-row>
        <div class="cron-hint">
          <span>常用示例：</span>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 3 * * ?'">每天3点</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 * * * ?'">每小时</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 30 6 * * ?'">每天6:30</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 12 * * ?'">每天12点</n-button>
        </div>
        <n-form-item v-if="form.taskType === 'cleanup'" label="保留天数">
          <n-input-number v-model:value="cleanupDays" :min="1" :max="365" style="width:100%;" />
          <div class="cron-hint" style="margin-top:4px;">超过此天数的数据将被清理</div>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="handleSave">{{ editingId ? '保存修改' : '创建任务' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Plus, Play, Edit3, Trash2 } from 'lucide-vue-next'
import request, { clearCache } from '@/api/request'

type ScheduledTaskType = 'cleanup'
type ScheduledTaskLastStatus = '' | 'success' | 'failed'

interface ScheduledTaskRecord {
  id: number
  name: string
  description: string
  taskType: ScheduledTaskType
  cron: string
  enabled: boolean | null
  configJson: string
  lastRunAt: string
  lastStatus: ScheduledTaskLastStatus
  createdAt: string
  updatedAt: string
}

const message = useMessage()
const loadingTasks = ref(true)
const tasks = ref<ScheduledTaskRecord[] | null>(null)
const runningId = ref<number | null>(null)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const tasksLoadState = ref<'loading' | 'ready' | 'error'>('loading')

const form = reactive({
  name: '', description: '', taskType: 'cleanup' as ScheduledTaskType, cron: '0 0 3 * * ?'
})
const cleanupDays = ref(30)
const tasksCountDisplay = computed(() => tasks.value === null ? '-' : tasks.value.length)

const typeOptions = [
  { label: '数据清理', value: 'cleanup' as ScheduledTaskType }
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

function normalizeScheduledTaskType(value: unknown): ScheduledTaskType {
  const normalized = normalizeOptionalText(value)
  if (normalized !== 'cleanup') {
    throw new Error('定时任务数据待确认')
  }
  return normalized
}

function normalizeLastStatus(value: unknown): ScheduledTaskLastStatus {
  const normalized = normalizeOptionalText(value)
  if (!normalized) {
    return ''
  }
  if (normalized !== 'success' && normalized !== 'failed') {
    throw new Error('定时任务数据待确认')
  }
  return normalized
}

function requireScheduledTasks(value: unknown): ScheduledTaskRecord[] {
  if (!Array.isArray(value)) {
    throw new Error('定时任务数据待确认')
  }
  const normalized = value.map(item => normalizeScheduledTaskRecord(item))
  const ids = new Set<number>()
  normalized.forEach((item) => {
    if (ids.has(item.id)) {
      throw new Error('定时任务数据待确认')
    }
    ids.add(item.id)
  })
  return normalized
}

function requireScheduledTaskResult(value: unknown, action: 'create' | 'update' | 'toggle') {
  if (!isPlainObject(value)) {
    if (action === 'create') throw new Error('任务创建结果待确认')
    if (action === 'update') throw new Error('任务更新结果待确认')
    throw new Error('任务状态待确认')
  }
  const id = Number(value.id)
  if (!Number.isFinite(id) || id <= 0) {
    if (action === 'create') throw new Error('任务创建结果待确认')
    if (action === 'update') throw new Error('任务更新结果待确认')
    throw new Error('任务状态待确认')
  }
  return {
    id,
    enabled: normalizeBinaryStatus((value as any).enabled)
  }
}

function normalizeScheduledTaskRecord(value: unknown): ScheduledTaskRecord {
  if (!isPlainObject(value)) {
    throw new Error('定时任务数据待确认')
  }
  const id = Number(value.id)
  const name = normalizeOptionalText(value.name)
  const taskType = normalizeScheduledTaskType(value.taskType)
  const cron = normalizeOptionalText(value.cron)
  if (!Number.isFinite(id) || id <= 0 || !name || !taskType || !cron) {
    throw new Error('定时任务数据待确认')
  }
  return {
    id,
    name,
    description: normalizeOptionalText(value.description),
    taskType,
    cron,
    enabled: normalizeBinaryStatus(value.enabled),
    configJson: typeof value.configJson === 'string' ? value.configJson : '',
    lastRunAt: typeof value.lastRunAt === 'string' ? value.lastRunAt : '',
    lastStatus: normalizeLastStatus(value.lastStatus),
    createdAt: typeof value.createdAt === 'string' ? value.createdAt : '',
    updatedAt: typeof value.updatedAt === 'string' ? value.updatedAt : ''
  }
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeCleanupDaysFromConfig(configJson: unknown): number | null {
  if (typeof configJson !== 'string' || !configJson.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(configJson)
    const value = Number(parsed?.daysOld)
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

function assertScheduledTaskMatches(task: ScheduledTaskRecord | null | undefined, expected: {
  name: string
  description: string
  taskType: ScheduledTaskType
  cron: string
  cleanupDays: number | null
}, action: 'create' | 'update') {
  if (
    !task
    || normalizeOptionalText(task.name) !== expected.name
    || normalizeOptionalText(task.description) !== expected.description
    || normalizeOptionalText(task.taskType) !== expected.taskType
    || normalizeOptionalText(task.cron) !== expected.cron
  ) {
    throw new Error(action === 'create' ? '任务创建结果待确认' : '任务更新结果待确认')
  }
  if (expected.taskType === 'cleanup' && normalizeCleanupDaysFromConfig(task.configJson) !== expected.cleanupDays) {
    throw new Error(action === 'create' ? '任务创建结果待确认' : '任务更新结果待确认')
  }
}

onMounted(load)

async function load() {
  clearCache('scheduled-tasks')
  loadingTasks.value = true
  tasksLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/scheduled-tasks', { headers: { 'x-no-cache': '1' } })
    const data = requireScheduledTasks(getResponseData(response, '定时任务数据待确认'))
    tasks.value = data
    tasksLoadState.value = 'ready'
  } catch (err: any) {
    tasks.value = null
    tasksLoadState.value = 'error'
    message.error(err.message || '加载定时任务失败')
  } finally {
    loadingTasks.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.description = ''
  form.taskType = 'cleanup'
  form.cron = '0 0 3 * * ?'
  cleanupDays.value = 30
  showEditor.value = true
}

function openEdit(task: ScheduledTaskRecord) {
  editingId.value = task.id
  form.name = task.name
  form.description = task.description || ''
  form.taskType = task.taskType
  form.cron = task.cron
  cleanupDays.value = 30
  if (task.configJson) {
    try { cleanupDays.value = JSON.parse(task.configJson).daysOld || 30 } catch {}
  }
  showEditor.value = true
}

async function handleSave() {
  if (!form.name || !form.cron) { message.error('名称和 Cron 表达式为必填'); return }
  const payload: Record<string, string> = {
    name: form.name,
    description: form.description,
    taskType: form.taskType,
    cron: form.cron
  }
  if (form.taskType === 'cleanup') {
    payload.configJson = JSON.stringify({ daysOld: cleanupDays.value })
  }
  const expected = {
    name: normalizeOptionalText(form.name),
    description: normalizeOptionalText(form.description),
    taskType: form.taskType,
    cron: normalizeOptionalText(form.cron),
    cleanupDays: form.taskType === 'cleanup' ? cleanupDays.value : null
  }
  try {
    const previousCount = tasks.value?.length
    if (editingId.value) {
      const currentEditingId = editingId.value
      const response = await request.put<unknown>(`/api/admin/scheduled-tasks/${currentEditingId}`, payload)
      requireScheduledTaskResult(getResponseData(response, '任务更新结果待确认'), 'update')
      await load()
      const refreshed = tasks.value?.find(task => task.id === currentEditingId)
      assertScheduledTaskMatches(refreshed, expected, 'update')
      if (typeof previousCount === 'number' && tasks.value?.length !== previousCount) {
        throw new Error('任务更新结果待确认')
      }
      message.success('任务已更新')
    } else {
      const response = await request.post<unknown>('/api/admin/scheduled-tasks', payload)
      const created = requireScheduledTaskResult(getResponseData(response, '任务创建结果待确认'), 'create')
      await load()
      const refreshed = tasks.value?.find(task => task.id === created.id)
      assertScheduledTaskMatches(refreshed, expected, 'create')
      if (typeof previousCount === 'number' && typeof tasks.value?.length === 'number' && tasks.value.length < previousCount + 1) {
        throw new Error('任务创建结果待确认')
      }
      message.success('任务已创建')
    }
    showEditor.value = false
  } catch (err: any) { message.error(err.message || '操作失败') }
}

async function handleDelete(id: number) {
  try {
    const previousCount = tasks.value?.length
    await request.delete(`/api/admin/scheduled-tasks/${id}`)
    await load()
    if (tasks.value?.some(task => task.id === id)) {
      throw new Error('删除结果待确认')
    }
    if (typeof previousCount === 'number' && typeof tasks.value?.length === 'number' && tasks.value.length > Math.max(0, previousCount - 1)) {
      throw new Error('删除结果待确认')
    }
    message.success('已删除')
  }
  catch (err: any) { message.error(err.message || '删除失败') }
}

async function handleToggle(id: number) {
  const task = tasks.value?.find(item => item.id === id)
  if (task?.enabled == null) {
    message.error('定时任务状态尚未明确，暂时无法切换')
    return
  }
  const expectedEnabled = !task.enabled
  try {
    const response = await request.post<unknown>(`/api/admin/scheduled-tasks/${id}/toggle`)
    requireScheduledTaskResult(getResponseData(response, '任务状态待确认'), 'toggle')
    await load()
    const refreshed = tasks.value?.find(item => item.id === id)
    if (!refreshed || refreshed.enabled !== expectedEnabled) {
      throw new Error('任务状态待确认')
    }
  } catch (err: any) {
    message.error(err.message || '操作失败')
  }
}

function normalizeBinaryStatus(value: unknown): boolean | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return true
  if (value === 0 || value === '0' || value === false || value === 'false') return false
  return null
}

async function handleRunNow(id: number) {
  runningId.value = id
  try {
    const currentTask = tasks.value?.find(task => task.id === id)
    const previousLastRunAt = typeof currentTask?.lastRunAt === 'string' ? currentTask.lastRunAt : ''
    const previousLastStatus = typeof currentTask?.lastStatus === 'string' ? currentTask.lastStatus : ''
    const response = await request.post<unknown>(`/api/admin/scheduled-tasks/${id}/run`)
    const responseTask = getResponseData(response, '任务触发结果待确认')
    if (responseTask != null) {
      throw new Error('任务触发结果待确认')
    }
    await load()
    const refreshed = tasks.value?.find(task => task.id === id)
    if (!refreshed) {
      throw new Error('任务触发结果待确认')
    }
    const nextLastRunAt = typeof refreshed.lastRunAt === 'string' ? refreshed.lastRunAt : ''
    const nextLastStatus = typeof refreshed.lastStatus === 'string' ? refreshed.lastStatus : ''
    if (!nextLastRunAt && !nextLastStatus) {
      throw new Error('任务触发结果待确认')
    }
    if (previousLastRunAt === nextLastRunAt && previousLastStatus === nextLastStatus) {
      throw new Error('任务触发结果待确认')
    }
    if (nextLastStatus !== 'success' && nextLastStatus !== 'failed') {
      throw new Error('任务触发结果待确认')
    }
    message.success('任务已触发')
  } catch (err: any) { message.error(err.message || '执行失败') }
  finally { runningId.value = null }
}
</script>

<style scoped>
.admin-scheduler { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.count { font-size: 12px; color: var(--text-muted); }
.status-note { margin-bottom: 12px; font-size: 12px; color: #fca5a5; }
.cron-hint { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12px; color: var(--text-muted); margin-bottom: 10px; }
.empty-cell { text-align: center; padding: 24px !important; color: var(--text-muted); }
</style>
