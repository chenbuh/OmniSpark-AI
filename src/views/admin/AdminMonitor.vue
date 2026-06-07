<template>
  <div class="admin-monitor">
    <div class="page-header">
      <h2>性能监控 (System Monitor)</h2>
      <p class="subtitle">按 5 秒轮询查看当前 JVM 进程与宿主机资源快照，不包含历史趋势与外部探针。</p>
      <n-button size="small" secondary @click="loadData" :loading="loading">刷新数据</n-button>
    </div>

    <div v-if="monitorLoadState === 'error'" class="status-note">监控数据待确认，请稍后重试。</div>
    <div v-else-if="loading && monitorLoadState === 'loading'" class="loading-box">
      <n-spin size="small" />
    </div>
    <div v-else class="status-hint">当前页展示的是管理端轮询快照，适合快速排查，不等同于完整可观测平台。</div>

    <template v-if="monitorLoadState === 'ready'">
    <n-row :gutter="16">
      <!-- CPU -->
      <n-col :span="8">
        <n-card class="glass-card monitor-card" :bordered="false">
          <template #header><span class="card-title">🖥 CPU</span></template>
          <div class="gauge-wrap">
            <svg viewBox="0 0 120 120" class="gauge">
              <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(255,255,255,0.04)" stroke-width="10" />
              <circle cx="60" cy="60" r="50" fill="none" stroke="#3b82f6" stroke-width="10"
                :stroke-dasharray="circum" :stroke-dashoffset="gaugeDashOffset(data.processCpuUsage)"
                :stroke-opacity="hasGaugeValue(data.processCpuUsage) ? 1 : 0"
                transform="rotate(-90 60 60)" stroke-linecap="round" />
              <text x="60" y="56" text-anchor="middle" fill="#f3f4f6" font-size="22" font-weight="700">{{ formatPercent(data.processCpuUsage) }}</text>
              <text x="60" y="72" text-anchor="middle" fill="#6b7280" font-size="10">CPU</text>
            </svg>
            <div class="gauge-info">
              <div class="info-row"><span>系统负载</span><span>{{ formatLoadAverage(data.cpu?.systemLoadAverage) }}</span></div>
              <div class="info-row"><span>核心数</span><span>{{ data.cpu?.availableProcessors ?? '-' }}</span></div>
              <div class="info-row"><span>进程 CPU</span><span>{{ formatPercent(data.processCpuUsage) }}</span></div>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 内存 -->
      <n-col :span="8">
        <n-card class="glass-card monitor-card" :bordered="false">
          <template #header><span class="card-title">💾 内存</span></template>
          <div class="gauge-wrap">
            <svg viewBox="0 0 120 120" class="gauge">
              <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(255,255,255,0.04)" stroke-width="10" />
              <circle cx="60" cy="60" r="50" fill="none" stroke="#10b981" stroke-width="10"
                :stroke-dasharray="circum" :stroke-dashoffset="gaugeDashOffset(data.memory?.heapUsagePercent)"
                :stroke-opacity="hasGaugeValue(data.memory?.heapUsagePercent) ? 1 : 0"
                transform="rotate(-90 60 60)" stroke-linecap="round" />
              <text x="60" y="56" text-anchor="middle" fill="#f3f4f6" font-size="22" font-weight="700">{{ formatPercent(data.memory?.heapUsagePercent) }}</text>
              <text x="60" y="72" text-anchor="middle" fill="#6b7280" font-size="10">堆内存</text>
            </svg>
            <div class="gauge-info">
              <div class="info-row"><span>已用</span><span>{{ data.memory?.heapUsedReadable ?? '-' }}</span></div>
              <div class="info-row"><span>最大</span><span>{{ data.memory?.heapMaxReadable ?? '-' }}</span></div>
              <div class="info-row"><span>非堆</span><span>{{ formatBytes(data.memory?.nonHeapUsed) }}</span></div>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- JVM -->
      <n-col :span="8">
        <n-card class="glass-card monitor-card" :bordered="false">
          <template #header><span class="card-title">⚙ JVM</span></template>
          <div class="info-list">
            <div class="info-row"><span>运行时间</span><span>{{ data.jvm?.uptimeReadable }}</span></div>
            <div class="info-row"><span>VM 名称</span><span>{{ data.jvm?.vmName }}</span></div>
            <div class="info-row"><span>VM 版本</span><span>{{ data.jvm?.vmVersion }}</span></div>
            <div class="info-row"><span>操作系统</span><span>{{ data.cpu?.osName }} {{ data.cpu?.osArch }}</span></div>
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 磁盘 -->
    <n-card class="glass-card" :bordered="false" style="margin-top:16px;">
      <template #header><span class="card-title">💿 磁盘</span></template>
      <n-table :single-line="false" class="monitor-table">
        <thead>
          <tr><th>路径</th><th style="width:200px">使用率</th><th style="width:120px">已用</th><th style="width:120px">可用</th><th style="width:120px">总量</th></tr>
        </thead>
        <tbody>
          <tr v-for="disk in diskRows" :key="disk.path">
            <td><code>{{ disk.path }}</code></td>
            <td>
              <n-progress type="line" :percentage="disk.usagePercent" :height="8"
                :status="disk.usagePercent > 90 ? 'error' : disk.usagePercent > 70 ? 'warning' : 'success'" />
            </td>
            <td>{{ disk.usedReadable }}</td>
            <td>{{ disk.freeReadable }}</td>
            <td>{{ disk.totalReadable }}</td>
          </tr>
          <tr v-if="diskRows.length === 0">
            <td colspan="5" class="empty-row">暂无磁盘数据</td>
          </tr>
        </tbody>
      </n-table>
      <div class="disk-summary" v-if="data.diskTotal">
        总计: {{ formatBytes(data.diskTotal) }} · 已用: {{ formatBytes(data.diskUsed) }} ({{ formatPercent(data.diskUsagePercent) }}) · 可用: {{ formatBytes(data.diskFree) }}
      </div>
    </n-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

interface MonitorCpuData {
  systemLoadAverage: number | null
  availableProcessors: number
  osName: string
  osArch: string
}

interface MonitorMemoryData {
  heapUsed: number
  heapMax: number
  heapUsedReadable: string
  heapMaxReadable: string
  heapUsagePercent: number | null
  nonHeapUsed: number | null
}

interface MonitorJvmData {
  uptimeReadable: string
  vmName: string
  vmVersion: string
}

interface MonitorDiskRow {
  path: string
  total: number
  free: number
  used: number
  usagePercent: number
  totalReadable: string
  freeReadable: string
  usedReadable: string
}

interface MonitorData {
  cpu: MonitorCpuData
  memory: MonitorMemoryData
  jvm: MonitorJvmData
  disks: MonitorDiskRow[]
  processCpuUsage?: number | null
  diskTotal?: number | null
  diskUsed?: number | null
  diskFree?: number | null
  diskUsagePercent?: number | null
}

const message = useMessage()

const loading = ref(false)
const data = ref<MonitorData>(emptyMonitorData())
const monitorLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const circum = 2 * Math.PI * 50 // 314.16
const diskRows = computed(() => data.value.disks)

let autoTimer: ReturnType<typeof setInterval> | null = null
let inFlight = false
let errorNotified = false
const POLL_INTERVAL_MS = 5000

onMounted(() => {
  loadData()
  autoTimer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      loadData()
    }
  }, POLL_INTERVAL_MS)
})

onUnmounted(() => { if (autoTimer !== null) clearInterval(autoTimer) })

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function emptyMonitorData(): MonitorData {
  return {
    cpu: {
      systemLoadAverage: null,
      availableProcessors: 0,
      osName: '',
      osArch: ''
    },
    memory: {
      heapUsed: 0,
      heapMax: 0,
      heapUsedReadable: '',
      heapMaxReadable: '',
      heapUsagePercent: null,
      nonHeapUsed: null
    },
    jvm: {
      uptimeReadable: '',
      vmName: '',
      vmVersion: ''
    },
    disks: [],
    processCpuUsage: null,
    diskTotal: null,
    diskUsed: null,
    diskFree: null,
    diskUsagePercent: null
  }
}

function requireDiskItem(value: unknown): MonitorDiskRow {
  if (!isPlainObject(value) || typeof value.path !== 'string') {
    throw new Error('监控数据待确认')
  }
  const total = toOptionalNumber(value.total)
  const free = toOptionalNumber(value.free)
  const used = toOptionalNumber(value.used)
  const usagePercent = toOptionalNumber(value.usagePercent)
  if (
    total == null || free == null || used == null || usagePercent == null
    || total < 0 || free < 0 || used < 0 || usagePercent < 0 || usagePercent > 100
    || free > total || used > total
  ) {
    throw new Error('监控数据待确认')
  }
  const path = value.path.trim()
  if (!path) {
    throw new Error('监控数据待确认')
  }
  return {
    path,
    total,
    free,
    used,
    usagePercent,
    totalReadable: typeof value.totalReadable === 'string' ? value.totalReadable : formatBytes(total),
    freeReadable: typeof value.freeReadable === 'string' ? value.freeReadable : formatBytes(free),
    usedReadable: typeof value.usedReadable === 'string' ? value.usedReadable : formatBytes(used)
  }
}

function requireMonitorData(value: unknown): MonitorData {
  if (!isPlainObject(value) || !isPlainObject(value.cpu) || !isPlainObject(value.memory) || !isPlainObject(value.jvm) || !Array.isArray(value.disks)) {
    throw new Error('监控数据待确认')
  }
  const cpu = value.cpu
  const memory = value.memory
  const jvm = value.jvm
  const availableProcessors = toOptionalNumber(cpu.availableProcessors)
  if (availableProcessors == null || availableProcessors < 0 || typeof cpu.osName !== 'string' || typeof cpu.osArch !== 'string') {
    throw new Error('监控数据待确认')
  }
  const systemLoadAverage = normalizeUnavailableMetric(cpu.systemLoadAverage)
  const heapUsed = toOptionalNumber(memory.heapUsed)
  const heapMax = toOptionalNumber(memory.heapMax)
  const heapUsagePercent = toOptionalNumber(memory.heapUsagePercent)
  const nonHeapUsed = toOptionalNumber(memory.nonHeapUsed)
  if (
    heapUsed == null || heapMax == null || heapUsed < 0 || heapMax < 0 || heapUsed > heapMax
    || typeof memory.heapUsedReadable !== 'string' || typeof memory.heapMaxReadable !== 'string'
    || (heapUsagePercent != null && (heapUsagePercent < 0 || heapUsagePercent > 100))
    || (nonHeapUsed != null && nonHeapUsed < 0)
  ) {
    throw new Error('监控数据待确认')
  }
  if (
    typeof jvm.uptimeReadable !== 'string' || !jvm.uptimeReadable.trim()
    || typeof jvm.vmName !== 'string' || !jvm.vmName.trim()
    || typeof jvm.vmVersion !== 'string' || !jvm.vmVersion.trim()
  ) {
    throw new Error('监控数据待确认')
  }
  const processCpuUsage = normalizeUnavailablePercent(value.processCpuUsage)
  const diskTotal = toOptionalNumber(value.diskTotal)
  const diskUsed = toOptionalNumber(value.diskUsed)
  const diskFree = toOptionalNumber(value.diskFree)
  const diskUsagePercent = toOptionalNumber(value.diskUsagePercent)
  if (
    (diskTotal != null && diskTotal < 0)
    || (diskUsed != null && diskUsed < 0)
    || (diskFree != null && diskFree < 0)
    || (diskUsagePercent != null && (diskUsagePercent < 0 || diskUsagePercent > 100))
  ) {
    throw new Error('监控数据待确认')
  }
  if (
    diskTotal != null && diskUsed != null && diskUsed > diskTotal
    || diskTotal != null && diskFree != null && diskFree > diskTotal
  ) {
    throw new Error('监控数据待确认')
  }
  return {
    cpu: {
      systemLoadAverage,
      availableProcessors,
      osName: cpu.osName.trim(),
      osArch: cpu.osArch.trim()
    },
    memory: {
      heapUsed,
      heapMax,
      heapUsedReadable: memory.heapUsedReadable,
      heapMaxReadable: memory.heapMaxReadable,
      heapUsagePercent,
      nonHeapUsed
    },
    jvm: {
      uptimeReadable: jvm.uptimeReadable.trim(),
      vmName: jvm.vmName.trim(),
      vmVersion: jvm.vmVersion.trim()
    },
    processCpuUsage,
    diskTotal,
    diskUsed,
    diskFree,
    diskUsagePercent,
    disks: value.disks.map((disk: unknown) => requireDiskItem(disk))
  }
}

async function loadData() {
  if (inFlight) return
  inFlight = true
  loading.value = true
  if (monitorLoadState.value !== 'ready') {
    monitorLoadState.value = 'loading'
  }
  try {
    const res = await request.get<unknown>('/api/admin/monitor')
    data.value = requireMonitorData(getResponseData(res, '监控数据待确认'))
    monitorLoadState.value = 'ready'
    errorNotified = false
  } catch (err: unknown) {
    data.value = emptyMonitorData()
    monitorLoadState.value = 'error'
    // 5 秒轮询,仅首次失败提示,避免重复弹窗刷屏
    if (!errorNotified) {
      message.error(err instanceof Error && err.message ? err.message : '加载监控数据失败')
      errorNotified = true
    }
  } finally {
    inFlight = false
    loading.value = false
  }
}

const gaugeOffset = (pct: number) => circum - (circum * Math.min(pct, 100) / 100)
const gaugeDashOffset = (value: unknown) => {
  const normalized = toOptionalNumber(value)
  return normalized == null ? circum : gaugeOffset(normalized)
}
const hasGaugeValue = (value: unknown) => toOptionalNumber(value) != null
const formatLoadAverage = (value: unknown) => {
  const normalized = toOptionalNumber(value)
  return normalized != null ? Math.round(normalized * 100) / 100 : '-'
}
const formatPercent = (value: unknown) => {
  const normalized = toOptionalNumber(value)
  return normalized != null ? Math.round(normalized * 10) / 10 + '%' : '-'
}
const formatBytes = (value: unknown) => {
  const b = toOptionalNumber(value)
  if (b == null || b < 0) return '-'
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / (1024 * 1024)).toFixed(1) + ' MB'
  return (b / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

function toOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

function normalizeUnavailableMetric(value: unknown): number | null {
  const normalized = toOptionalNumber(value)
  if (normalized == null) {
    return null
  }
  return normalized < 0 ? null : normalized
}

function normalizeUnavailablePercent(value: unknown): number | null {
  const normalized = normalizeUnavailableMetric(value)
  if (normalized == null) {
    return null
  }
  if (normalized > 100) {
    throw new Error('监控数据待确认')
  }
  return normalized
}
</script>

<style scoped>
.admin-monitor { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; display: flex; justify-content: space-between; align-items: flex-end; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.loading-box { display: flex; justify-content: center; padding: 32px 0; }
.status-note { margin-bottom: 16px; font-size: 12px; color: #fca5a5; }
.status-hint { margin-bottom: 16px; font-size: 12px; color: #9ca3af; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.card-title { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.monitor-card { min-height: 280px; }

.gauge-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.gauge { width: 120px; height: 120px; }

.gauge-info, .info-list { width: 100%; display: flex; flex-direction: column; gap: 6px; }
.info-row { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-secondary); padding: 4px 0; border-bottom: 1px solid var(--border-light); }

.monitor-table { background: transparent !important; }
.monitor-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; font-size: 12px; }
.monitor-table td { color: var(--text-secondary); padding: 8px; font-size: 13px; }
.empty-row { text-align: center; color: #9ca3af; padding: 16px 0; }

.disk-summary { font-size: 12px; color: #9ca3af; padding: 8px 0 0; text-align: center; }
</style>
