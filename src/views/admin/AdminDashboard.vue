<template>
  <div class="admin-dashboard">
    <div class="page-header">
      <h2>管理控制台 (Admin Dashboard)</h2>
      <p class="subtitle">系统全局概览与统计。</p>
      <n-button size="small" secondary style="margin-top:8px;" @click="handleExportCsv">
        <template #icon><Download /></template>导出 CSV 报表
      </n-button>
    </div>

    <!-- 统计卡片 -->
    <n-row :gutter="16">
      <n-col :span="4" v-for="item in statCards" :key="item.key">
        <n-card class="glass-card stat-card" :bordered="false">
          <div class="stat-inner">
            <span class="stat-label">{{ item.label }}</span>
            <span class="stat-value" :style="{ color: item.color }">{{ stats[item.key] ?? '-' }}</span>
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 图表行 -->
    <n-row :gutter="16" style="margin-top:20px;">
      <!-- 每日任务趋势柱状图 -->
      <n-col :span="12">
        <n-card class="glass-card chart-card" :bordered="false">
          <template #header><span class="chart-title">每日任务趋势</span></template>
          <div class="chart-box">
            <svg v-if="dailyTasksSeries.length > 0" :viewBox="`0 0 ${barChartW} ${barChartH}`" class="chart-svg">
              <!-- Y轴网格线 -->
              <line v-for="i in 4" :key="'g'+i" :x1="40" :y1="barChartH - (i * barStepY)" :x2="barChartW - 10" :y2="barChartH - (i * barStepY)" stroke="rgba(255,255,255,0.04)" stroke-width="1" />
              <!-- 柱状图 -->
              <g v-for="(d, idx) in dailyTasksSeries" :key="'b'+idx">
                <rect :x="barX(idx)" :y="barY(d.count)" :width="barW" :height="barH(d.count)" :fill="barColors[idx]" rx="4" />
                <text :x="barX(idx) + barW/2" :y="barChartH - 6" text-anchor="middle" fill="#6b7280" font-size="10">{{ d.date }}</text>
                <text :x="barX(idx) + barW/2" :y="barY(d.count) - 6" text-anchor="middle" fill="#d1d5db" font-size="11" font-weight="600">{{ d.count }}</text>
              </g>
            </svg>
            <div v-else class="chart-empty">
              {{ trendStatus === 'error' ? '任务趋势待确认，请稍后重试。' : trendStatus === 'loading' ? '正在加载任务趋势...' : '暂无任务趋势' }}
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 任务状态分布环形图 -->
      <n-col :span="6">
        <n-card class="glass-card chart-card" :bordered="false">
          <template #header><span class="chart-title">任务状态分布</span></template>
          <div class="chart-box chart-box--center">
            <div class="donut-box">
              <svg viewBox="0 0 200 200" class="chart-svg donut-svg">
              <!-- 环形图三段 -->
              <circle cx="100" cy="100" r="70" fill="none" stroke="rgba(255,255,255,0.04)" stroke-width="28" />
              <circle
                v-for="segment in donutSegments"
                :key="segment.key"
                cx="100"
                cy="100"
                r="70"
                fill="none"
                :stroke="segment.color"
                :stroke-width="28"
                :stroke-dasharray="`${segment.length} ${circum}`"
                :stroke-dashoffset="segment.offset"
                transform="rotate(-90 100 100)"
                stroke-linecap="round"
              />
              <text x="100" y="95" text-anchor="middle" fill="#f3f4f6" font-size="24" font-weight="700">{{ taskTotalLabel }}</text>
              <text x="100" y="115" text-anchor="middle" fill="#6b7280" font-size="10">总任务</text>
              </svg>
              <div class="donut-legend">
                <div class="legend-item"><span class="dot" style="background:#6366f1"></span>排队中 {{ displayStatsMetric('pendingTasks') }}</div>
                <div class="legend-item"><span class="dot" style="background:#f59e0b"></span>运行中 {{ displayStatsMetric('runningTasks') }}</div>
                <div class="legend-item"><span class="dot" style="background:#10b981"></span>成功 {{ displayStatsMetric('successTasks') }}</div>
                <div class="legend-item"><span class="dot" style="background:#ef4444"></span>失败 {{ displayStatsMetric('failedTasks') }}</div>
              </div>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 每日注册用户趋势 -->
      <n-col :span="6">
        <n-card class="glass-card chart-card" :bordered="false">
          <template #header><span class="chart-title">用户注册趋势</span></template>
          <div class="chart-box">
            <svg v-if="dailyUsersSeries.length > 0" viewBox="0 0 300 160" class="chart-svg">
              <!-- 折线图 -->
              <polyline :points="linePoints" fill="none" stroke="#3b82f6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <!-- 面积填充 -->
              <path :d="areaPath" fill="url(#areaGrad)" opacity="0.3" />
              <defs>
                <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.5" />
                  <stop offset="100%" stop-color="#3b82f6" stop-opacity="0" />
                </linearGradient>
              </defs>
              <!-- 数据点 -->
              <circle v-for="(d, idx) in dailyUsersSeries" :key="'p'+idx" :cx="lineX(idx)" :cy="lineY(d.count)" r="3" fill="#3b82f6" stroke="#05070c" stroke-width="1.5" />
              <!-- 标签 -->
              <text v-for="(d, idx) in dailyUsersSeries" :key="'l'+idx" :x="lineX(idx)" y="150" text-anchor="middle" fill="#6b7280" font-size="9">{{ d.date }}</text>
            </svg>
            <div v-else class="chart-empty">
              {{ trendStatus === 'error' ? '用户趋势待确认，请稍后重试。' : trendStatus === 'loading' ? '正在加载用户趋势...' : '暂无用户趋势' }}
            </div>
          </div>
        </n-card>
      </n-col>
    </n-row>

    <!-- 最近用户 -->
    <n-card class="glass-card" :bordered="false" style="margin-top:20px;">
      <template #header><span style="font-weight:600;color:#e5e7eb;">最近注册用户</span></template>
      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="u in recentUsers || []" :key="u.id">
            <td><code>#{{ u.id }}</code></td>
            <td>{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td><n-tag size="small" :type="u.role === 'admin' ? 'warning' : 'info'">{{ u.role }}</n-tag></td>
            <td><n-tag size="small" :type="userStatusTagType(u.status)">{{ userStatusLabel(u.status) }}</n-tag></td>
          </tr>
          <tr v-if="recentUsersStatus !== 'ready' || !recentUsers || recentUsers.length === 0">
            <td colspan="5" class="table-empty-cell">
              {{ recentUsersStatus === 'error' ? '最近注册用户待确认，请稍后重试。' : recentUsersStatus === 'loading' ? '正在加载最近注册用户...' : '暂无最近注册用户' }}
            </td>
          </tr>
        </tbody>
      </n-table>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Download } from 'lucide-vue-next'
import request, { API_BASE_URL } from '@/api/request'

const message = useMessage()
const loading = ref(true)
const stats = ref<Record<string, number>>({})
const recentUsers = ref<any[] | null>(null)
const dailyTasks = ref<{date:string;count:number}[] | null>(null)
const dailyUsers = ref<{date:string;count:number}[] | null>(null)
const trendStatus = ref<'loading' | 'ready' | 'error'>('loading')
const recentUsersStatus = ref<'loading' | 'ready' | 'error'>('loading')

const statCards = [
  { key: 'totalUsers', label: '总用户数', color: '#3b82f6' },
  { key: 'totalProjects', label: '总项目数', color: '#10b981' },
  { key: 'totalTasks', label: '总任务数', color: '#f59e0b' },
  { key: 'totalAssets', label: '总资产数', color: '#8b5cf6' },
  { key: 'successTasks', label: '成功任务', color: '#10b981' },
  { key: 'failedTasks', label: '失败任务', color: '#ef4444' }
]

const barColors = ['#3b82f6','#10b981','#f59e0b','#8b5cf6','#ef4444','#ec4899','#14b8a6']
const barChartW = 500
const barChartH = 200
const barStepY = 40
const dailyTasksSeries = computed(() => dailyTasks.value ?? [])
const dailyUsersSeries = computed(() => dailyUsers.value ?? [])
const maxCount = computed(() => Math.max(...dailyTasksSeries.value.map(d => d.count), 1))
const barW = computed(() => Math.min(40, (barChartW - 80) / Math.max(dailyTasksSeries.value.length, 1) - 6))
const barX = (i: number) => 45 + i * (barW.value + 6)
const barY = (c: number) => barChartH - 30 - (c / maxCount.value) * (barChartH - 50)
const barH = (c: number) => (c / maxCount.value) * (barChartH - 50)

// 环形图
const taskTotal = computed(() => {
  const values = ['pendingTasks', 'runningTasks', 'successTasks', 'failedTasks']
    .map(key => toOptionalNumber(stats.value[key]))
    .filter((value): value is number => value != null)
  if (values.length === 0) return null
  return values.reduce((sum, value) => sum + value, 0)
})
const taskTotalLabel = computed(() => taskTotal.value == null ? (loading.value ? '加载中' : '-') : String(taskTotal.value))
const circum = 2 * Math.PI * 70 // 439.8
const donutSegments = computed(() => {
  const segments = [
    { key: 'pending', color: '#6366f1', value: toOptionalNumber(stats.value.pendingTasks) },
    { key: 'running', color: '#f59e0b', value: toOptionalNumber(stats.value.runningTasks) },
    { key: 'success', color: '#10b981', value: toOptionalNumber(stats.value.successTasks) },
    { key: 'failed', color: '#ef4444', value: toOptionalNumber(stats.value.failedTasks) }
  ].filter((segment): segment is { key: string, color: string, value: number } => segment.value != null)

  let offset = 0
  return segments.flatMap((segment) => {
    const total = taskTotal.value
    const length = total != null && total > 0 ? (segment.value / total) * circum : 0
    if (length <= 0) return []

    const current = {
      key: segment.key,
      color: segment.color,
      length,
      offset: -offset
    }
    offset += length
    return [current]
  })
})

// 折线图
const lineW = 260, lineH = 130, linePad = 30
const lineMax = computed(() => Math.max(...dailyUsersSeries.value.map(d => d.count), 1))
const lineX = (i: number) => linePad + i * ((lineW - linePad * 2) / Math.max(dailyUsersSeries.value.length - 1, 1))
const lineY = (c: number) => lineH - 20 - (c / lineMax.value) * (lineH - 40)
const linePoints = computed(() => dailyUsersSeries.value.map((d, i) => `${lineX(i)},${lineY(d.count)}`).join(' '))
const areaPath = computed(() => {
  if (dailyUsersSeries.value.length === 0) return ''
  const pts = dailyUsersSeries.value.map((d, i) => `${lineX(i)},${lineY(d.count)}`).join(' L ')
  return `M ${lineX(0)},${lineH - 20} L ${pts} L ${lineX(dailyUsersSeries.value.length - 1)},${lineH - 20} Z`
})

const handleExportCsv = () => {
  const token = localStorage.getItem('satoken')
  const a = document.createElement('a')
  a.href = `${API_BASE_URL}/api/admin/stats/export/csv`
  a.download = `stats_${new Date().toISOString().slice(0, 10)}.csv`
  // Use fetch with auth to trigger download
  fetch(a.href, { headers: { 'satoken': token || '' } })
    .then(res => res.blob())
    .then(blob => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = a.download
      link.click()
      URL.revokeObjectURL(url)
      message.success('报表已下载')
    })
    .catch(() => message.error('导出失败'))
}

const loadDashboard = async () => {
  try {
    loading.value = true
    trendStatus.value = 'loading'
    recentUsersStatus.value = 'loading'
    const [overviewRes, trendsRes, usersRes] = await Promise.allSettled([
      request.get('/api/admin/stats/overview'),
      request.get('/api/admin/stats/trends'),
      request.get('/api/admin/stats/users', { params: { page: 1, pageSize: 10 } })
    ])
    let failedCount = 0

    if (overviewRes.status === 'fulfilled') {
      const overviewData = (overviewRes.value as any).data
      if (overviewData && typeof overviewData === 'object' && !Array.isArray(overviewData)) {
        stats.value = overviewData
      } else {
        stats.value = {}
        failedCount += 1
      }
    } else {
      stats.value = {}
      failedCount += 1
    }

    if (trendsRes.status === 'fulfilled') {
      const trends = (trendsRes.value as any).data
      const hasValidTrendPayload = !!trends
        && typeof trends === 'object'
        && !Array.isArray(trends)
        && Array.isArray((trends as any).dailyTasks)
        && Array.isArray((trends as any).dailyUsers)
      if (hasValidTrendPayload) {
        dailyTasks.value = (trends as any).dailyTasks
        dailyUsers.value = (trends as any).dailyUsers
        trendStatus.value = 'ready'
      } else {
        dailyTasks.value = null
        dailyUsers.value = null
        trendStatus.value = 'error'
        failedCount += 1
      }
    } else {
      dailyTasks.value = null
      dailyUsers.value = null
      trendStatus.value = 'error'
      failedCount += 1
    }

    if (usersRes.status === 'fulfilled') {
      const records = (usersRes.value as any).data?.records
      if (Array.isArray(records)) {
        recentUsers.value = records
        recentUsersStatus.value = 'ready'
      } else {
        recentUsers.value = null
        recentUsersStatus.value = 'error'
        failedCount += 1
      }
    } else {
      recentUsers.value = null
      recentUsersStatus.value = 'error'
      failedCount += 1
    }

    if (failedCount === 3) {
      message.error('加载仪表盘数据失败')
    } else if (failedCount > 0) {
      message.warning('部分仪表盘数据待确认')
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)

function normalizeUserStatus(value: unknown): number | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return 1
  if (value === 0 || value === '0' || value === false || value === 'false') return 0
  return null
}

function userStatusTagType(value: unknown) {
  const normalized = normalizeUserStatus(value)
  if (normalized === null) return 'warning'
  return normalized === 1 ? 'success' : 'error'
}

function userStatusLabel(value: unknown) {
  const normalized = normalizeUserStatus(value)
  if (normalized === null) return '状态待确认'
  return normalized === 1 ? '正常' : '禁用'
}

function displayStatsMetric(key: string) {
  const normalized = toOptionalNumber(stats.value[key])
  if (normalized == null) {
    return loading.value ? '加载中' : '-'
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
</script>

<style scoped>
.admin-dashboard { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.stat-card { text-align: center; padding: 10px; }
.stat-inner { display: flex; flex-direction: column; gap: 4px; }
.stat-label { font-size: 11px; color: #9ca3af; }
.stat-value { font-size: 22px; font-weight: 700; }
.chart-card { min-height: 240px; }
.chart-title { font-size: 14px; font-weight: 600; color: #e5e7eb; }
.chart-box { width: 100%; height: 220px; display: flex; align-items: center; }
.chart-box--center { justify-content: center; }
.chart-svg { width: 100%; height: 100%; }
.chart-empty { width: 100%; text-align: center; color: #9ca3af; font-size: 13px; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 13px; }
.table-empty-cell { text-align: center; color: #9ca3af !important; }
.donut-box { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
.donut-svg { max-width: 170px; max-height: 170px; flex: 0 0 auto; }
.donut-legend { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #d1d5db; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
</style>
