<template>
  <div class="admin-access-logs">
    <div class="page-header">
      <h2>访问日志</h2>
      <p class="subtitle">按 IP、用户、API Key、接口、状态码、耗时和限流命中追踪访问行为。</p>
    </div>

    <div class="summary-grid">
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
        <div v-for="item in summary.topIps || []" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
      </n-card>
      <n-card class="glass-card" :bordered="false">
        <h3>高频接口</h3>
        <div v-for="item in summary.topPaths || []" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
      </n-card>
      <n-card class="glass-card" :bordered="false">
        <h3>状态码</h3>
        <div v-for="item in summary.statusCodes || []" :key="item.name" class="rank-row">
          <code>{{ item.name || '-' }}</code><span>{{ item.count }}</span>
        </div>
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

const message = useMessage()
const loading = ref(false)
const summaryLoading = ref(false)
const logs = ref<any[] | null>(null)
const summary = ref<Record<string, any>>({})
const total = ref<number | null>(null)
const page = ref(1)
const pageSize = 20
const filters = reactive({
  clientIp: '',
  path: '',
  userId: null as number | null,
  apiKeyId: null as number | null,
  statusCode: null as number | null
})

const summaryCards = computed(() => [
  { label: '窗口', value: formatSummaryWindow(summary.value.windowMinutes) },
  { label: '总请求', value: formatSummaryMetric(summary.value.total) },
  { label: '限流命中', value: formatSummaryMetric(summary.value.rateLimited) },
  { label: '风险命中', value: formatSummaryMetric(summary.value.riskHits) }
])
const totalDisplay = computed(() => total.value == null ? '-' : total.value)

async function loadSummary() {
  summaryLoading.value = true
  try {
    const res = await request.get('/api/admin/access-logs/summary', { params: { minutes: 60 } })
    summary.value = (res as any).data || {}
  } finally {
    summaryLoading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params: Record<string, any> = { page: page.value, pageSize }
    Object.entries(filters).forEach(([key, value]) => {
      if (value !== null && value !== '') params[key] = value
    })
    const res = await request.get('/api/admin/access-logs', { params })
    const data = (res as any).data || {}
    logs.value = data.records || []
    total.value = typeof data.total === 'number' ? data.total : 0
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
    return summaryLoading.value ? '加载中' : '-'
  }
  return `${normalized} 分钟`
}

function formatSummaryMetric(value: unknown) {
  const normalized = toOptionalNumber(value)
  if (normalized == null) {
    return summaryLoading.value ? '加载中' : '-'
  }
  return normalized
}

function toOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isNaN(normalized) ? null : normalized
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
.metric-label { font-size: 12px; color: #9ca3af; }
.metric-value { margin-top: 10px; font-size: 26px; font-weight: 700; color: #f8fafc; }
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
