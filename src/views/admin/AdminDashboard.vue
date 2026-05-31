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
            <svg :viewBox="`0 0 ${barChartW} ${barChartH}`" class="chart-svg">
              <!-- Y轴网格线 -->
              <line v-for="i in 4" :key="'g'+i" :x1="40" :y1="barChartH - (i * barStepY)" :x2="barChartW - 10" :y2="barChartH - (i * barStepY)" stroke="rgba(255,255,255,0.04)" stroke-width="1" />
              <!-- 柱状图 -->
              <g v-for="(d, idx) in dailyTasks" :key="'b'+idx">
                <rect :x="barX(idx)" :y="barY(d.count)" :width="barW" :height="barH(d.count)" :fill="barColors[idx]" rx="4" />
                <text :x="barX(idx) + barW/2" :y="barChartH - 6" text-anchor="middle" fill="#6b7280" font-size="10">{{ d.date }}</text>
                <text :x="barX(idx) + barW/2" :y="barY(d.count) - 6" text-anchor="middle" fill="#d1d5db" font-size="11" font-weight="600">{{ d.count }}</text>
              </g>
            </svg>
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
              <text x="100" y="95" text-anchor="middle" fill="#f3f4f6" font-size="24" font-weight="700">{{ taskTotal }}</text>
              <text x="100" y="115" text-anchor="middle" fill="#6b7280" font-size="10">总任务</text>
              </svg>
              <div class="donut-legend">
                <div class="legend-item"><span class="dot" style="background:#10b981"></span>成功 {{ stats.successTasks ?? 0 }}</div>
                <div class="legend-item"><span class="dot" style="background:#f59e0b"></span>运行中 {{ stats.runningTasks ?? 0 }}</div>
                <div class="legend-item"><span class="dot" style="background:#ef4444"></span>失败 {{ stats.failedTasks ?? 0 }}</div>
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
            <svg viewBox="0 0 300 160" class="chart-svg">
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
              <circle v-for="(d, idx) in dailyUsers" :key="'p'+idx" :cx="lineX(idx)" :cy="lineY(d.count)" r="3" fill="#3b82f6" stroke="#05070c" stroke-width="1.5" />
              <!-- 标签 -->
              <text v-for="(d, idx) in dailyUsers" :key="'l'+idx" :x="lineX(idx)" y="150" text-anchor="middle" fill="#6b7280" font-size="9">{{ d.date }}</text>
            </svg>
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
          <tr v-for="u in recentUsers" :key="u.id">
            <td><code>#{{ u.id }}</code></td>
            <td>{{ u.username }}</td>
            <td>{{ u.nickname || '-' }}</td>
            <td><n-tag size="small" :type="u.role === 'admin' ? 'warning' : 'info'">{{ u.role }}</n-tag></td>
            <td><n-tag size="small" :type="u.status === 1 ? 'success' : 'error'">{{ u.status === 1 ? '正常' : '禁用' }}</n-tag></td>
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
const stats = ref<Record<string, number>>({})
const recentUsers = ref<any[]>([])
const dailyTasks = ref<{date:string;count:number}[]>([])
const dailyUsers = ref<{date:string;count:number}[]>([])

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
const maxCount = computed(() => Math.max(...dailyTasks.value.map(d => d.count), 1))
const barW = computed(() => Math.min(40, (barChartW - 80) / dailyTasks.value.length - 6))
const barX = (i: number) => 45 + i * (barW.value + 6)
const barY = (c: number) => barChartH - 30 - (c / maxCount.value) * (barChartH - 50)
const barH = (c: number) => (c / maxCount.value) * (barChartH - 50)

// 环形图
const taskTotal = computed(() => (stats.value.successTasks || 0) + (stats.value.runningTasks || 0) + (stats.value.failedTasks || 0))
const circum = 2 * Math.PI * 70 // 439.8
const donutSegments = computed(() => {
  const segments = [
    { key: 'success', color: '#10b981', value: stats.value.successTasks || 0 },
    { key: 'running', color: '#f59e0b', value: stats.value.runningTasks || 0 },
    { key: 'failed', color: '#ef4444', value: stats.value.failedTasks || 0 }
  ]

  let offset = 0
  return segments.flatMap((segment) => {
    const length = taskTotal.value > 0 ? (segment.value / taskTotal.value) * circum : 0
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
const lineMax = computed(() => Math.max(...dailyUsers.value.map(d => d.count), 1))
const lineX = (i: number) => linePad + i * ((lineW - linePad * 2) / Math.max(dailyUsers.value.length - 1, 1))
const lineY = (c: number) => lineH - 20 - (c / lineMax.value) * (lineH - 40)
const linePoints = computed(() => dailyUsers.value.map((d, i) => `${lineX(i)},${lineY(d.count)}`).join(' '))
const areaPath = computed(() => {
  if (dailyUsers.value.length === 0) return ''
  const pts = dailyUsers.value.map((d, i) => `${lineX(i)},${lineY(d.count)}`).join(' L ')
  return `M ${lineX(0)},${lineH - 20} L ${pts} L ${lineX(dailyUsers.value.length - 1)},${lineH - 20} Z`
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

onMounted(async () => {
  try {
    const [overviewRes, trendsRes, usersRes] = await Promise.all([
      request.get('/api/admin/stats/overview'),
      request.get('/api/admin/stats/trends'),
      request.get('/api/admin/stats/users', { params: { page: 1, pageSize: 10 } })
    ])
    stats.value = (overviewRes as any).data || {}
    const trends = (trendsRes as any).data || {}
    dailyTasks.value = trends.dailyTasks || []
    dailyUsers.value = trends.dailyUsers || []
    recentUsers.value = (usersRes as any).data?.records || []

    // 计算 running 数量
    const total = (stats.value.totalTasks || 0)
    const success = (stats.value.successTasks || 0)
    const failed = (stats.value.failedTasks || 0)
    stats.value.runningTasks = Math.max(0, total - success - failed)
  } catch (err: any) {
    message.error(err.message || '加载仪表盘数据失败')
  }
})
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
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 13px; }
.donut-box { width: 100%; height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; }
.donut-svg { max-width: 170px; max-height: 170px; flex: 0 0 auto; }
.donut-legend { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #d1d5db; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
</style>
