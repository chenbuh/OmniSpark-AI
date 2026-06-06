<template>
  <div class="admin-access-logs">
    <div class="page-header">
      <h2>访问日志</h2>
      <p class="subtitle">按 IP、用户、API Key、接口、状态码、耗时和限流命中追踪访问行为。</p>
    </div>

    <div class="summary-grid">
      <n-card v-if="summaryLoadState === 'error'" class="glass-card metric-card metric-card--wide" :bordered="false">
        <div class="status-note">访问日志汇总待确认，请稍后重试。</div>
      </n-card>
      <n-card v-for="item in summaryCards" :key="item.label" class="glass-card metric-card" :bordered="false">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
      </n-card>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="filter-row">
        <n-space align="center" :size="12">
          <n-input v-model:value="filters.clientIp" size="small" placeholder="IP" clearable style="width: 150px" />
          <n-input v-model:value="filters.path" size="small" placeholder="接口路径" clearable style="width: 220px" />
          <n-input-number v-model:value="filters.userId" size="small" placeholder="用户ID" clearable style="width: 120px" />
          <n-input-number v-model:value="filters.apiKeyId" size="small" placeholder="API Key ID" clearable style="width: 130px" />
          <n-input-number v-model:value="filters.statusCode" size="small" placeholder="状态码" clearable style="width: 110px" />
          <n-button size="small" type="primary" :loading="loading" @click="reload">查询</n-button>
          <n-button size="small" tertiary @click="resetFilters">重置</n-button>
        </n-space>
        <span class="count-lbl">共 {{ totalDisplay }} 条</span>
      </div>
    </n-card>

    <div class="detail-grid">
      <n-card class="glass-card" :bordered="false">
        <h3>高频 IP</h3>
        <div v-for="item in summaryTopIps" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
        <div v-if="summaryLoadState === 'error'" class="empty">访问日志汇总待确认</div>
      </n-card>
      <n-card class="glass-card" :bordered="false">
        <h3>高频接口</h3>
        <div v-for="item in summaryTopPaths" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
        <div v-if="summaryLoadState === 'error'" class="empty">访问日志汇总待确认</div>
      </n-card>
      <n-card class="glass-card" :bordered="false">
        <h3>状态码</h3>
        <div v-for="item in summaryStatusCodes" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
        <div v-if="summaryLoadState === 'error'" class="empty">访问日志汇总待确认</div>
      </n-card>
    </div>

    <n-card class="glass-card" :bordered="false">
      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr>
            <th style="width: 70px">ID</th>
            <th style="width: 90px">用户</th>
            <th style="width: 90px">Key</th>
            <th style="width: 130px">IP</th>
            <th style="width: 70px">方法</th>
            <th>路径</th>
            <th style="width: 80px">状态</th>
            <th style="width: 90px">耗时</th>
            <th style="width: 90px">限流</th>
            <th style="width: 160px">时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs || []" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.userId || '-' }}</td>
            <td>{{ log.apiKeyId || '-' }}</td>
            <td><code>{{ log.clientIp || '-' }}</code></td>
            <td><n-tag size="small">{{ log.method }}</n-tag></td>
            <td>
              <n-ellipsis :line-clamp="1" :tooltip="true">
                {{ log.path }}<span v-if="log.queryString">?{{ log.queryString }}</span>
              </n-ellipsis>
              <div v-if="log.riskReason" class="risk-line">{{ log.riskReason }}</div>
            </td>
            <td><n-tag size="small" :type="statusType(log.statusCode)">{{ log.statusCode }}</n-tag></td>
            <td>{{ log.durationMs }}ms</td>
            <td>
              <n-tag v-if="log.rateLimited" size="small" type="error">命中</n-tag>
              <span v-else>-</span>
            </td>
            <td>{{ formatTime(log.createdAt) }}</td>
          </tr>
          <tr v-if="logs !== null && logs.length === 0"><td colspan="10" class="empty">暂无访问日志</td></tr>
          <tr v-else-if="logs === null"><td colspan="10" class="empty">访问日志数据待确认</td></tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="(total ?? 0) > 0">
        <n-pagination
          v-model:page="page"
          :page-size="pageSize"
          :item-count="total"
          @update:page="loadLogs"
        />
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

interface AccessLogSummaryRow {
  name: string
  count: number
}

interface AccessLogSummary {
  windowMinutes: number
  total: number
  rateLimited: number
  riskHits: number
  topIps: AccessLogSummaryRow[]
  topPaths: AccessLogSummaryRow[]
  statusCodes: AccessLogSummaryRow[]
}

interface AccessLogRecord {
  id: number
  userId: number | null
  apiKeyId: number | null
  clientIp: string
  userAgent: string
  method: string
  path: string
  queryString: string
  statusCode: number
  durationMs: number
  rateLimited: boolean
  riskReason: string
  createdAt: string
}

interface AccessLogPage {
  records: AccessLogRecord[]
  total: number
}

const message = useMessage()
const loading = ref(false)
const summaryLoading = ref(false)
const summaryLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const logs = ref<AccessLogRecord[] | null>(null)
const summary = ref<AccessLogSummary | null>(null)
const total = ref<number | null>(null)
const page = ref(1)
const pageSize = 20
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
const filters = reactive({
  clientIp: '',
  path: '',
  userId: null as number | null,
  apiKeyId: null as number | null,
  statusCode: null as number | null
})

const summaryCards = computed(() => [
  { label: '窗口', value: formatSummaryWindow(summary.value?.windowMinutes) },
  { label: '总请求', value: formatSummaryMetric(summary.value?.total) },
  { label: '限流命中', value: formatSummaryMetric(summary.value?.rateLimited) },
  { label: '风险命中', value: formatSummaryMetric(summary.value?.riskHits) }
])
const totalDisplay = computed(() => total.value == null ? '-' : total.value)
const summaryTopIps = computed(() => summary.value?.topIps || [])
const summaryTopPaths = computed(() => summary.value?.topPaths || [])
const summaryStatusCodes = computed(() => summary.value?.statusCodes || [])

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function normalizeSummaryRows(value: unknown): AccessLogSummaryRow[] {
  if (!Array.isArray(value)) {
    throw new Error('访问日志汇总待确认')
  }
  const normalized = value.map((item) => {
    if (!isPlainObject(item)) {
      throw new Error('访问日志汇总待确认')
    }
    const count = toOptionalNumber(item.count)
    if (count == null || count < 0) {
      throw new Error('访问日志汇总待确认')
    }
    return {
      name: typeof item.name === 'string' ? item.name.trim() : '',
      count
    }
  })
  const names = new Set<string>()
  normalized.forEach(item => {
    const key = `${item.name}::${item.count}`
    if (names.has(key)) {
      throw new Error('访问日志汇总待确认')
    }
    names.add(key)
  })
  return normalized
}

function normalizeAccessLogRecord(value: unknown): AccessLogRecord {
  if (!isPlainObject(value)) {
    throw new Error('访问日志数据待确认')
  }
  const id = requirePositiveNumber(value.id, '访问日志数据待确认')
  const method = normalizeOptionalText(value.method)
  const path = normalizeOptionalText(value.path)
  const statusCode = requireNonNegativeNumber(value.statusCode, '访问日志数据待确认')
  const durationMs = requireNonNegativeNumber(value.durationMs, '访问日志数据待确认')
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!method || !path || !createdAt || statusCode < 100) {
    throw new Error('访问日志数据待确认')
  }
  const rateLimited = normalizeBinaryStatus(value.rateLimited)
  if (rateLimited === null) {
    throw new Error('访问日志数据待确认')
  }
  const userId = toOptionalNumber(value.userId)
  const apiKeyId = toOptionalNumber(value.apiKeyId)
  if ((userId != null && userId <= 0) || (apiKeyId != null && apiKeyId <= 0)) {
    throw new Error('访问日志数据待确认')
  }
  return {
    id,
    userId,
    apiKeyId,
    clientIp: normalizeOptionalText(value.clientIp),
    userAgent: normalizeOptionalText(value.userAgent),
    method,
    path,
    queryString: normalizeOptionalText(value.queryString),
    statusCode,
    durationMs,
    rateLimited: rateLimited === 1,
    riskReason: normalizeOptionalText(value.riskReason),
    createdAt
  }
}

function requireAccessLogSummary(value: unknown): AccessLogSummary {
  if (!isPlainObject(value)) {
    throw new Error('访问日志汇总待确认')
  }
  const windowMinutes = requirePositiveNumber(value.windowMinutes, '访问日志汇总待确认')
  const totalRequests = requireNonNegativeNumber(value.total, '访问日志汇总待确认')
  const rateLimited = requireNonNegativeNumber(value.rateLimited, '访问日志汇总待确认')
  const riskHits = requireNonNegativeNumber(value.riskHits, '访问日志汇总待确认')
  if (rateLimited > totalRequests || riskHits > totalRequests) {
    throw new Error('访问日志汇总待确认')
  }
  return {
    windowMinutes,
    total: totalRequests,
    rateLimited,
    riskHits,
    topIps: normalizeSummaryRows(value.topIps),
    topPaths: normalizeSummaryRows(value.topPaths),
    statusCodes: normalizeSummaryRows(value.statusCodes)
  }
}

function requireAccessLogPage(value: unknown): AccessLogPage {
  if (!isPlainObject(value)) {
    throw new Error('访问日志数据待确认')
  }
  const records = value.records
  const count = requireNonNegativeNumber(value.total, '访问日志数据待确认')
  if (!Array.isArray(records)) {
    throw new Error('访问日志数据待确认')
  }
  const normalizedRecords = records.map(item => normalizeAccessLogRecord(item))
  const ids = new Set<number>()
  normalizedRecords.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('访问日志数据待确认')
    }
    ids.add(item.id)
  })
  if (normalizedRecords.length > count) {
    throw new Error('访问日志数据待确认')
  }
  return {
    records: normalizedRecords,
    total: count
  }
}

async function loadSummary() {
  summaryLoading.value = true
  summaryLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/access-logs/summary', {
      params: { minutes: 60 },
      headers: NO_CACHE_HEADERS
    })
    summary.value = requireAccessLogSummary(getResponseData(response, '访问日志汇总待确认'))
    summaryLoadState.value = 'ready'
  } catch (err: any) {
    summary.value = null
    summaryLoadState.value = 'error'
    message.error(err.message || '加载访问日志汇总失败')
  } finally {
    summaryLoading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params: Record<string, number | string> = { page: page.value, pageSize }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== null && value !== '') params[key] = value
    })
    const response = await request.get<unknown>('/api/admin/access-logs', { params, headers: NO_CACHE_HEADERS })
    const data = requireAccessLogPage(getResponseData(response, '访问日志数据待确认'))
    logs.value = data.records
    total.value = data.total
  } catch (err: any) {
    logs.value = null
    total.value = null
    message.error(err.message || '加载访问日志失败')
  } finally {
    loading.value = false
  }
}

async function reload() {
  page.value = 1
  await Promise.allSettled([loadSummary(), loadLogs()])
}

function resetFilters() {
  filters.clientIp = ''
  filters.path = ''
  filters.userId = null
  filters.apiKeyId = null
  filters.statusCode = null
  reload()
}

function statusType(status?: number) {
  if (!status) return 'default'
  if (status >= 500) return 'error'
  if (status === 429 || status === 403 || status === 401) return 'warning'
  if (status >= 400) return 'error'
  return 'success'
}

function formatTime(value?: string) {
  return value?.substring(0, 19)?.replace('T', ' ') || '-'
}

function formatSummaryWindow(value: unknown) {
  const normalized = toOptionalNumber(value)
  if (normalized == null) {
    if (summaryLoadState.value === 'error') {
      return '待确认'
    }
    return summaryLoading.value ? '加载中' : '-'
  }
  return `${normalized} 分钟`
}

function formatSummaryMetric(value: unknown) {
  const normalized = toOptionalNumber(value)
  if (normalized == null) {
    if (summaryLoadState.value === 'error') {
      return '待确认'
    }
    return summaryLoading.value ? '加载中' : '-'
  }
  return normalized
}

function toOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeBinaryStatus(value: unknown): number | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return 1
  if (value === 0 || value === '0' || value === false || value === 'false') return 0
  return null
}

function requirePositiveNumber(value: unknown, errorMessage: string) {
  const normalized = toOptionalNumber(value)
  if (normalized == null || normalized <= 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function requireNonNegativeNumber(value: unknown, errorMessage: string) {
  const normalized = toOptionalNumber(value)
  if (normalized == null || normalized < 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

onMounted(reload)
</script>

<style scoped>
.admin-access-logs { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 16px; }
.detail-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin: 16px 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.metric-card { min-height: 90px; }
.metric-card--wide { grid-column: 1 / -1; min-height: auto; }
.metric-label { font-size: 12px; color: #9ca3af; }
.metric-value { margin-top: 10px; font-size: 26px; font-weight: 700; color: #f8fafc; }
.status-note { font-size: 12px; color: #fca5a5; }
.filter-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.count-lbl { font-size: 12px; color: #9ca3af; white-space: nowrap; }
h3 { margin: 0 0 12px 0; font-size: 14px; color: #e5e7eb; }
.rank-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 7px 0; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 12px; color: #cbd5e1; }
.rank-row code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 12px; }
.risk-line { margin-top: 4px; color: #f59e0b; font-size: 11px; }
.empty { text-align: center; padding: 30px; color: #6b7280; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
@media (max-width: 1100px) {
  .summary-grid, .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-row { align-items: flex-start; flex-direction: column; }
}
</style>
