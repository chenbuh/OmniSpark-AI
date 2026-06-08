<template>
  <div class="admin-login-logs">
    <div class="page-header">
      <h2>登录日志 (Login Logs)</h2>
      <p class="subtitle">查看所有用户的登录记录、真实来源 IP 与地区详情。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="filter-row">
        <n-space align="center" :size="12">
          <n-input-number v-model:value="filters.userId" size="small" placeholder="用户ID" clearable style="width: 120px" />
          <n-input v-model:value="filters.username" size="small" placeholder="用户名" clearable style="width: 160px" />
          <n-input v-model:value="filters.ip" size="small" placeholder="IP" clearable style="width: 160px" />
          <n-button size="small" type="primary" :loading="loading" @click="reload">查询</n-button>
          <n-button size="small" tertiary @click="resetFilters">重置</n-button>
        </n-space>
        <span class="count-lbl">共 {{ total ?? 0 }} 条</span>
      </div>
    </n-card>

    <n-card class="glass-card" :bordered="false" style="margin-top: 16px;">
      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr>
            <th style="width:60px">ID</th>
            <th style="width:80px">用户ID</th>
            <th style="width:140px">用户名</th>
            <th style="width:150px">IP</th>
            <th style="width:180px">地区</th>
            <th>User Agent</th>
            <th style="width:160px">登录时间</th>
            <th style="width:80px">详情</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs || []" :key="log.id">
            <td><code>#{{ log.id }}</code></td>
            <td>{{ log.userId }}</td>
            <td>{{ log.username }}</td>
            <td><code>{{ log.ip }}</code></td>
            <td>
              <div>{{ formatIpGeoSummary(log.ipGeo) }}</div>
              <div v-if="log.ipGeo?.isp" class="sub-line">{{ log.ipGeo?.isp }}</div>
            </td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.userAgent }}</n-ellipsis></td>
            <td>{{ log.createdAt?.substring(0, 19)?.replace('T', ' ') }}</td>
            <td><n-button text type="primary" size="small" @click="openDetail(log)">详情</n-button></td>
          </tr>
          <tr v-if="logs !== null && logs.length === 0"><td colspan="8" class="empty">暂无登录记录</td></tr>
          <tr v-else-if="logs === null"><td colspan="8" class="empty">登录日志数据待确认</td></tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="(total ?? 0) > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="loadLogs" />
      </div>
    </n-card>

    <n-drawer v-model:show="detailVisible" :width="620" placement="right">
      <n-drawer-content title="登录记录详情" closable>
        <template v-if="selectedLog">
          <div class="detail-grid">
            <div class="detail-item"><span>记录ID</span><strong>#{{ selectedLog.id }}</strong></div>
            <div class="detail-item"><span>用户ID</span><strong>{{ selectedLog.userId }}</strong></div>
            <div class="detail-item"><span>用户名</span><strong>{{ selectedLog.username }}</strong></div>
            <div class="detail-item"><span>登录时间</span><strong>{{ formatTime(selectedLog.createdAt) }}</strong></div>
            <div class="detail-item detail-item--wide"><span>来源 IP</span><strong><code>{{ selectedLog.ip || '-' }}</code></strong></div>
            <div class="detail-item detail-item--wide"><span>地区摘要</span><strong>{{ formatIpGeoSummary(selectedLog.ipGeo) }}</strong></div>
            <div class="detail-item"><span>国家</span><strong>{{ selectedLog.ipGeo?.country || '-' }}</strong></div>
            <div class="detail-item"><span>省/州</span><strong>{{ selectedLog.ipGeo?.region || '-' }}</strong></div>
            <div class="detail-item"><span>城市</span><strong>{{ selectedLog.ipGeo?.city || '-' }}</strong></div>
            <div class="detail-item"><span>邮编</span><strong>{{ selectedLog.ipGeo?.postalCode || '-' }}</strong></div>
            <div class="detail-item"><span>时区</span><strong>{{ selectedLog.ipGeo?.timezoneId || selectedLog.ipGeo?.timezoneUtc || '-' }}</strong></div>
            <div class="detail-item"><span>运营商</span><strong>{{ selectedLog.ipGeo?.isp || '-' }}</strong></div>
            <div class="detail-item"><span>组织</span><strong>{{ selectedLog.ipGeo?.organization || '-' }}</strong></div>
            <div class="detail-item"><span>ASN</span><strong>{{ selectedLog.ipGeo?.asn ?? '-' }}</strong></div>
            <div class="detail-item detail-item--wide"><span>网络特征</span><strong>{{ formatFlags(selectedLog.ipGeo) }}</strong></div>
            <div class="detail-item detail-item--wide"><span>坐标</span><strong>{{ formatCoordinates(selectedLog.ipGeo) }}</strong></div>
            <div class="detail-item detail-item--wide"><span>归属说明</span><strong>{{ selectedLog.ipGeo?.detailMessage || '-' }}</strong></div>
            <div class="detail-item detail-item--wide"><span>User Agent</span><strong class="break-all">{{ selectedLog.userAgent || '-' }}</strong></div>
          </div>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'
import { formatIpGeoSummary, normalizeIpGeoInfo, type IpGeoInfo } from '@/utils/ipGeo'

interface LoginLogRecord {
  id: number
  userId: number
  username: string
  ip: string
  userAgent: string
  createdAt: string
  ipGeo: IpGeoInfo | null
}

const message = useMessage()
const loading = ref(false)
const logs = ref<LoginLogRecord[] | null>(null)
const page = ref(1)
const pageSize = 20
const total = ref<number | null>(null)
const detailVisible = ref(false)
const selectedLog = ref<LoginLogRecord | null>(null)
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
const filters = reactive({
  userId: null as number | null,
  username: '',
  ip: ''
})

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error('登录日志数据待确认')
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function requirePositiveNumber(value: unknown, errorMessage: string) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized <= 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function normalizeLoginLogRecord(value: unknown): LoginLogRecord {
  if (!isPlainObject(value)) {
    throw new Error('登录日志数据待确认')
  }
  const id = requirePositiveNumber(value.id, '登录日志数据待确认')
  const userId = requirePositiveNumber(value.userId, '登录日志数据待确认')
  const username = normalizeOptionalText(value.username)
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!username || !createdAt) {
    throw new Error('登录日志数据待确认')
  }
  return {
    id,
    userId,
    username,
    ip: normalizeOptionalText(value.ip),
    userAgent: normalizeOptionalText(value.userAgent),
    createdAt,
    ipGeo: normalizeIpGeoInfo(value.ipGeo)
  }
}

function requireLoginLogPage(value: unknown): { records: LoginLogRecord[]; total: number } {
  if (!isPlainObject(value)) {
    throw new Error('登录日志数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('登录日志数据待确认')
  }
  const normalizedRecords = records.map(item => normalizeLoginLogRecord(item))
  const ids = new Set<number>()
  normalizedRecords.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('登录日志数据待确认')
    }
    ids.add(item.id)
  })
  if (normalizedRecords.length > count) {
    throw new Error('登录日志数据待确认')
  }
  return {
    records: normalizedRecords,
    total: count
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const params: Record<string, string | number> = { page: page.value, pageSize }
    if (filters.userId != null) params.userId = filters.userId
    if (filters.username.trim()) params.username = filters.username.trim()
    if (filters.ip.trim()) params.ip = filters.ip.trim()
    const response = await request.get<unknown>('/api/admin/login-logs', {
      params,
      headers: NO_CACHE_HEADERS
    })
    const data = requireLoginLogPage(getResponseData(response))
    logs.value = data.records
    total.value = data.total
  } catch (err: unknown) {
    logs.value = null
    total.value = null
    message.error(getErrorMessage(err, '加载登录日志失败'))
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  void loadLogs()
}

function resetFilters() {
  filters.userId = null
  filters.username = ''
  filters.ip = ''
  reload()
}

function openDetail(log: LoginLogRecord) {
  selectedLog.value = log
  detailVisible.value = true
}

function formatTime(value?: string) {
  return value?.substring(0, 19)?.replace('T', ' ') || '-'
}

function formatCoordinates(ipGeo: IpGeoInfo | null) {
  if (ipGeo?.latitude == null || ipGeo?.longitude == null) {
    return '-'
  }
  return `${ipGeo.latitude.toFixed(4)}, ${ipGeo.longitude.toFixed(4)}`
}

function formatFlags(ipGeo: IpGeoInfo | null) {
  if (!ipGeo) return '-'
  const labels = [
    ipGeo.privateNetwork ? '内网/保留地址' : '',
    ipGeo.proxy ? '代理' : '',
    ipGeo.vpn ? 'VPN' : '',
    ipGeo.tor ? 'Tor' : '',
    ipGeo.hosting ? '机房' : ''
  ].filter(Boolean)
  return labels.length > 0 ? labels.join(' / ') : '-'
}

onMounted(() => {
  void loadLogs()
})
</script>

<style scoped>
.admin-login-logs { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.filter-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.count-lbl { font-size: 12px; color: #9ca3af; white-space: nowrap; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 12px; }
.sub-line { margin-top: 4px; font-size: 11px; color: #94a3b8; }
.empty { text-align: center; padding: 30px; color: #6b7280; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.detail-item { padding: 12px; border-radius: 12px; background: rgba(148, 163, 184, 0.08); display: flex; flex-direction: column; gap: 8px; }
.detail-item span { font-size: 12px; color: #94a3b8; }
.detail-item strong { color: #e5e7eb; font-size: 13px; font-weight: 600; }
.detail-item--wide { grid-column: 1 / -1; }
.break-all { word-break: break-all; }
@media (max-width: 960px) {
  .filter-row { flex-direction: column; align-items: flex-start; }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
