<template>
  <div class="audit-container">
    <div class="page-header">
      <h2>审计日志 (Audit Logs)</h2>
      <p class="subtitle">系统操作审计记录，追踪所有用户的关键操作行为。</p>
    </div>

    <!-- 过滤器 -->
    <n-card class="glass-card filter-card" :bordered="false">
      <div class="filter-row">
        <n-space align="center" :size="16">
          <n-select
            v-model:value="actionFilter"
            :options="actionOptions"
            :placeholder="actionFilterPlaceholder"
            :disabled="actionOptionsLoadState === 'error' || actionOptionsLoadState === 'loading'"
            style="width: 160px;"
            clearable
            @update:value="onFilter"
          />
          <n-button type="primary" size="small" @click="loadLogs">刷新</n-button>
          <!-- 仅管理员可清理 N 天前日志 -->
          <template v-if="isAdmin">
            <n-divider vertical />
            <span class="cleanup-lbl">清理</span>
            <n-input-number v-model:value="cleanupDays" :min="7" :max="3650" size="small" style="width: 110px;">
              <template #suffix>天前</template>
            </n-input-number>
            <n-button type="error" size="small" tertiary :loading="cleaning" @click="handleCleanup">清理日志</n-button>
          </template>
        </n-space>
        <span class="count-lbl">共 {{ totalDisplay }} 条记录</span>
      </div>
      <div v-if="actionOptionsLoadState === 'error'" class="status-note">审计操作类型待确认，请稍后重试。</div>
    </n-card>

    <!-- 日志表格 -->
    <n-card class="glass-card" :bordered="false" style="margin-top: 16px;">
      <n-table :single-line="false" class="audit-table">
        <thead>
          <tr>
            <th style="width: 80px;">ID</th>
            <th style="width: 100px;">用户</th>
            <th style="width: 140px;">操作</th>
            <th>详情</th>
            <th style="width: 120px;">IP</th>
            <th style="width: 160px;">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs || []" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.username || '未知' }}</td>
            <td><n-tag size="small" :type="actionColor(log.action)">{{ formatAction(log.action) }}</n-tag></td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.detail }}</n-ellipsis></td>
            <td><code>{{ log.ip || '-' }}</code></td>
            <td>{{ log.createdAt?.substring(0, 19)?.replace('T', ' ') }}</td>
          </tr>
          <tr v-if="logs !== null && logs.length === 0">
            <td colspan="6" class="empty-cell">暂无审计日志</td>
          </tr>
          <tr v-else-if="logs === null">
            <td colspan="6" class="empty-cell">审计日志数据待确认</td>
          </tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="(total ?? 0) > 0">
        <n-pagination
          v-model:page="page"
          :page-size="pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="pageSizeOptions"
          @update:page="loadLogs"
          @update:page-size="handlePageSizeChange"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import request from '@/api/request'
import { useUserStore } from '@/store/user'

const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.userInfo?.role === 'admin')

type AuditLogRecord = {
  id: number
  username: string
  action: string
  detail: string
  ip: string
  createdAt: string
}

const logs = ref<AuditLogRecord[] | null>(null)
const actionFilter = ref<string | null>(null)
const page = ref(1)
const pageSize = ref(20)
const pageSizeOptions = [20, 50, 100]
const total = ref<number | null>(null)
const cleanupDays = ref(30)
const cleaning = ref(false)
const actions = ref<string[]>([])
const actionOptionsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
const actionOptions = computed(() => [
  { label: '全部', value: '' },
  ...actions.value.map(action => ({
    label: formatAction(action),
    value: action
  }))
])
const totalDisplay = computed(() => total.value == null ? '-' : total.value)
const actionFilterPlaceholder = computed(() => {
  if (actionOptionsLoadState.value === 'error') {
    return '操作类型待确认'
  }
  if (actionOptionsLoadState.value === 'loading') {
    return '操作类型加载中'
  }
  return '操作类型'
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

function getErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

const actionColor = (action: string) => {
  if (!action) return 'default'
  if (action.includes('delete') || action.includes('remove')) return 'error'
  if (action.includes('create') || action.includes('add') || action.includes('register')) return 'success'
  if (action.includes('login') || action.includes('logout')) return 'info'
  if (action.includes('test') || action.includes('retry')) return 'warning'
  return 'default'
}

const formatAction = (action: string) => {
  if (!action) return '-'
  return action.replace(/_/g, ' ')
}

const toOptionalNumber = (value: unknown): number | null => {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isNaN(normalized) ? null : normalized
}

function requireAuditLogRecord(value: unknown): AuditLogRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('审计日志数据待确认')
  }
  const record = value as Record<string, unknown>
  const id = Number(record.id)
  const action = typeof record.action === 'string' ? record.action.trim() : ''
  const createdAt = typeof record.createdAt === 'string' ? record.createdAt.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !action || !createdAt) {
    throw new Error('审计日志数据待确认')
  }
  return {
    id,
    username: typeof record.username === 'string' ? record.username.trim() : '',
    action,
    detail: typeof record.detail === 'string' ? record.detail.trim() : '',
    ip: typeof record.ip === 'string' ? record.ip.trim() : '',
    createdAt
  }
}

function requireNonNegativeNumber(value: unknown, errorMessage: string) {
  const normalized = toOptionalNumber(value)
  if (normalized == null || normalized < 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function requireAuditLogPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('审计日志数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('审计日志数据待确认')
  }
  const seenIds = new Set<number>()
  const normalizedRecords = records.map((item: unknown) => {
    const normalized = requireAuditLogRecord(item)
    if (seenIds.has(normalized.id)) {
      throw new Error('审计日志数据待确认')
    }
    seenIds.add(normalized.id)
    return normalized
  })
  if (normalizedRecords.length > count) {
    throw new Error('审计日志数据待确认')
  }
  return {
    records: normalizedRecords,
    total: count
  }
}

function requireAuditActionList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('审计操作类型待确认')
  }
  const normalized: string[] = []
  const seen = new Set<string>()
  value.forEach((item: unknown) => {
    const action = typeof item === 'string' ? item.trim() : ''
    if (!action || seen.has(action)) {
      throw new Error('审计操作类型待确认')
    }
    seen.add(action)
    normalized.push(action)
  })
  return normalized
}

async function loadLogs() {
  try {
    const params: Record<string, string | number> = { page: page.value, size: pageSize.value }
    if (actionFilter.value) params.action = actionFilter.value
    const endpoint = isAdmin.value ? '/api/audit-logs' : '/api/audit-logs/my'
    const res = await request.get(endpoint, { params, headers: NO_CACHE_HEADERS })
    const data = requireAuditLogPage(getResponseData(res, '审计日志数据待确认'))
    logs.value = data.records
    total.value = data.total
  } catch (err: unknown) {
    logs.value = null
    total.value = null
    message.error(getErrorMessage(err, '加载审计日志失败'))
  }
}

async function loadCleanupPreview(noCache = false) {
  const res = await request.get('/api/audit-logs/cleanup-preview', {
    params: { daysOld: cleanupDays.value },
    headers: noCache ? NO_CACHE_HEADERS : undefined
  })
  return requireNonNegativeNumber(getResponseData(res, '清理结果待确认'), '清理结果待确认')
}

function requireCleanupConfirmed(
  deletedCount: number,
  previewBefore: number,
  previewAfter: number,
  previousTotal: number | null,
  currentTotal: number | null
) {
  if (deletedCount < 0 || previewBefore < 0 || previewAfter < 0) {
    throw new Error('清理结果待确认')
  }
  if (previewAfter > previewBefore) {
    throw new Error('清理结果待确认')
  }
  if (previewAfter > Math.max(0, previewBefore - deletedCount)) {
    throw new Error('清理结果待确认')
  }
  if (previewBefore > 0 && deletedCount === 0) {
    throw new Error('清理结果待确认')
  }
  if (previousTotal != null && currentTotal == null) {
    throw new Error('清理结果待确认')
  }
  if (previousTotal != null && currentTotal != null && currentTotal > previousTotal) {
    throw new Error('清理结果待确认')
  }
}

async function loadActions() {
  actionOptionsLoadState.value = 'loading'
  try {
    const res = await request.get('/api/audit-logs/actions', { headers: NO_CACHE_HEADERS })
    actions.value = requireAuditActionList(getResponseData(res, '审计操作类型待确认'))
    actionOptionsLoadState.value = 'ready'
    if (actionFilter.value && !actions.value.includes(actionFilter.value)) {
      actionFilter.value = null
    }
  } catch {
    actions.value = []
    actionOptionsLoadState.value = 'error'
  }
}

// 切换操作类型过滤时回到第 1 页
function onFilter() {
  page.value = 1
  loadLogs()
}

// 切换每页条数时回到第 1 页
function handlePageSizeChange(size: number) {
  pageSize.value = size
  page.value = 1
  loadLogs()
}

// 管理员清理 N 天前的审计日志
function handleCleanup() {
  dialog.warning({
    title: '清理审计日志',
    content: `确定删除 ${cleanupDays.value} 天前的所有审计日志吗？此操作不可恢复。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      cleaning.value = true
      try {
        const previousTotal = total.value
        const previewBefore = await loadCleanupPreview(true)
        const res = await request.delete('/api/audit-logs', { params: { daysOld: cleanupDays.value } })
        const deletedCount = requireNonNegativeNumber(getResponseData(res, '清理结果待确认'), '清理结果待确认')
        page.value = 1
        const params: Record<string, string | number> = { page: page.value, size: pageSize.value }
        if (actionFilter.value) params.action = actionFilter.value
        const endpoint = isAdmin.value ? '/api/audit-logs' : '/api/audit-logs/my'
        const refreshRes = await request.get(endpoint, { params, headers: NO_CACHE_HEADERS })
        const refreshData = requireAuditLogPage(getResponseData(refreshRes, '审计日志数据待确认'))
        logs.value = refreshData.records
        total.value = refreshData.total
        const previewAfter = await loadCleanupPreview(true)
        if (logs.value === null || total.value == null) {
          throw new Error('清理结果待确认')
        }
        requireCleanupConfirmed(deletedCount, previewBefore, previewAfter, previousTotal, total.value)
        message.success(`已清理 ${deletedCount} 条审计日志`)
      } catch (err: unknown) {
        message.error(getErrorMessage(err, '清理失败'))
      } finally {
        cleaning.value = false
      }
    }
  })
}

onMounted(async () => {
  await loadActions()
  await loadLogs()
})
</script>

<style scoped>
.audit-container { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card {
  background: rgba(15,23,42,0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255,255,255,0.08) !important;
  border-radius: 16px !important;
}
.filter-row { display: flex; justify-content: space-between; align-items: center; }
.count-lbl { font-size: 12px; color: #9ca3af; }
.cleanup-lbl { font-size: 12px; color: #9ca3af; }
.status-note { margin-top: 12px; font-size: 12px; color: #fca5a5; }

.audit-table { background-color: transparent !important; }
.audit-table th {
  background-color: rgba(255,255,255,0.02) !important;
  color: #9ca3af !important;
  border-bottom: 1px solid rgba(255,255,255,0.06) !important;
  font-size: 12px;
}
.audit-table td {
  border-bottom: 1px solid rgba(255,255,255,0.04) !important;
  color: #e5e7eb;
  padding: 12px 10px;
  font-size: 12px;
}
.empty-cell { text-align: center !important; padding: 40px !important; color: #6b7280; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
