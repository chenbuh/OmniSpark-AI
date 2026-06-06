<template>
  <div class="stats-container">
    <div class="page-header">
      <div>
        <h2>用量统计分析 (Usage Stats)</h2>
        <p class="subtitle">观察任务趋势、额度消耗、项目活跃度和最近操作，帮助判断当前创作负载。</p>
      </div>
      <n-space>
        <n-select v-model:value="scopeMode" :options="scopeOptions" style="width: 170px;" />
        <n-button secondary :loading="loading" @click="refreshPageData">
          <template #icon><RefreshCw /></template>
          刷新数据
        </n-button>
      </n-space>
    </div>
    <div v-if="statsLoadState === 'error'" class="status-note">统计数据待确认，请稍后重试。</div>

    <div class="stats-summary-grid">
      <n-card v-for="card in summaryCards" :key="card.key" class="glass-card summary-card" :bordered="false">
        <span class="summary-label">{{ card.label }}</span>
        <strong class="summary-value" :class="card.color">{{ card.value }}</strong>
        <span class="summary-hint">{{ card.hint }}</span>
      </n-card>
    </div>

    <n-row :gutter="20" class="section-grid">
      <n-col :span="16">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="section-header">
              <span>最近 14 天任务与额度趋势</span>
              <small>{{ scopeDetailLabel }}内的每日创作节奏</small>
            </div>
          </template>
          <div v-if="statsLoadState === 'error'" class="stats-empty-box">
            <n-empty description="趋势数据待确认，请稍后重试。" style="padding: 24px 0;" />
          </div>
          <div v-else-if="trendDays.length === 0" class="stats-empty-box">
            <n-empty description="暂无趋势数据" style="padding: 24px 0;" />
          </div>
          <div v-else class="trend-chart-box">
            <svg viewBox="0 0 760 280" class="trend-chart">
              <defs>
                <linearGradient id="taskTrendFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#10b981" stop-opacity="0.32" />
                  <stop offset="100%" stop-color="#10b981" stop-opacity="0" />
                </linearGradient>
                <linearGradient id="quotaBars" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#f59e0b" stop-opacity="0.9" />
                  <stop offset="100%" stop-color="#f59e0b" stop-opacity="0.25" />
                </linearGradient>
              </defs>

              <line v-for="grid in trendGridLines" :key="grid.key" :x1="60" :y1="grid.y" :x2="720" :y2="grid.y" class="chart-grid" />

              <g v-for="bar in quotaBars" :key="bar.key">
                <rect :x="bar.x - 10" :y="bar.y" width="20" :height="bar.height" rx="6" fill="url(#quotaBars)" opacity="0.7" />
              </g>

              <template v-if="taskTrendPath">
                <path :d="taskTrendAreaPath" fill="url(#taskTrendFill)" />
                <path :d="taskTrendPath" class="trend-line" />
                <circle v-for="point in taskTrendPoints" :key="point.key" :cx="point.x" :cy="point.y" r="4.5" class="trend-dot" />
              </template>

              <text v-for="tick in yAxisTicks" :key="tick.key" :x="48" :y="tick.y + 4" class="axis-label" text-anchor="end">{{ tick.label }}</text>
              <text v-for="label in xAxisLabels" :key="label.key" :x="label.x" y="252" class="axis-label" text-anchor="middle">{{ label.label }}</text>
            </svg>
          </div>
          <div class="chart-legend-row">
            <div class="legend-item">
              <span class="legend-dot green"></span>
              <span>任务量</span>
            </div>
            <div class="legend-item">
              <span class="legend-dot amber"></span>
              <span>额度消耗</span>
            </div>
          </div>
        </n-card>
      </n-col>

      <n-col :span="8">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="section-header">
              <span>生成类型占比</span>
              <small>图像与视频的任务结构</small>
            </div>
          </template>
          <div class="distribution-box">
            <svg viewBox="0 0 220 220" width="180" height="180">
              <circle cx="110" cy="110" r="64" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="24" />
              <circle
                cx="110"
                cy="110"
                r="64"
                fill="none"
                stroke="#10b981"
                stroke-width="24"
                :stroke-dasharray="typeImageDash"
                stroke-dashoffset="0"
                transform="rotate(-90 110 110)"
                stroke-linecap="round"
              />
              <circle
                cx="110"
                cy="110"
                r="64"
                fill="none"
                stroke="#f59e0b"
                stroke-width="24"
                :stroke-dasharray="typeVideoDash"
                :stroke-dashoffset="-typeImageLength"
                transform="rotate(-90 110 110)"
                stroke-linecap="round"
              />
              <text x="110" y="104" class="donut-total" text-anchor="middle">{{ formatOptionalNumber(overview.taskCount) }}</text>
              <text x="110" y="126" class="donut-label" text-anchor="middle">任务总数</text>
            </svg>
            <div class="legend-col">
              <div class="legend-item">
                <span class="legend-dot green"></span>
                <span>图像任务 {{ formatOptionalPercent(imageTaskPercent, 0) }}</span>
              </div>
              <div class="legend-item">
                <span class="legend-dot amber"></span>
                <span>视频任务 {{ formatOptionalPercent(videoTaskPercent, 0) }}</span>
              </div>
            </div>
          </div>
        </n-card>

        <n-card class="glass-card status-card" :bordered="false">
          <template #header>
            <div class="section-header">
              <span>任务状态分布</span>
              <small>当前范围内的完成质量</small>
            </div>
          </template>
          <div class="status-stack">
            <div v-for="item in statusCards" :key="item.key" class="status-row">
              <div class="status-meta">
                <span class="status-name">{{ item.label }}</span>
                <span class="status-value">{{ formatOptionalNumber(item.value) }}</span>
              </div>
              <n-progress
                v-if="item.percent !== null"
                type="line"
                :percentage="item.percent"
                :show-indicator="false"
                :height="8"
                :color="item.color"
                rail-color="rgba(255,255,255,0.06)"
              />
              <span v-else class="pending-text">数据待确认</span>
            </div>
          </div>
        </n-card>
      </n-col>
    </n-row>

    <n-row :gutter="20" class="section-grid">
      <n-col :span="14">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="section-header">
              <span>项目空间活跃排行</span>
              <small>对比任务量、成功率和额度消耗</small>
            </div>
          </template>
          <div v-if="projectRankings.length > 0" class="project-ranking-list">
            <div v-for="item in projectRankings" :key="item.projectId ?? item.name" class="project-ranking-item">
              <div class="project-row-top">
                <div class="project-title">
                  <span class="rank-badge">{{ formatOptionalNumber(item.rank) }}</span>
                  <div>
                    <strong>{{ item.name }}</strong>
                    <small>{{ item.description || '暂无项目描述' }}</small>
                  </div>
                </div>
                <span class="project-last-active">{{ formatDateTime(item.lastActiveAt) }}</span>
              </div>
              <div class="project-metrics">
                <span>任务 {{ formatOptionalNumber(item.taskCount) }}</span>
                <span>成功率 {{ formatOptionalPercent(item.successRate, 0) }}</span>
                <span>资产 {{ formatOptionalNumber(item.assetCount) }}</span>
                <span>额度 {{ formatOptionalNumber(item.quotaUsed) }}</span>
              </div>
              <n-progress
                v-if="toOptionalNumber(item.weightPercent) !== null"
                type="line"
                :percentage="toOptionalNumber(item.weightPercent) || 0"
                :show-indicator="false"
                :height="8"
                :color="item.rank === 1 ? '#10b981' : item.rank === 2 ? '#3b82f6' : '#f59e0b'"
                rail-color="rgba(255,255,255,0.05)"
              />
              <span v-else class="pending-text">权重数据待确认</span>
            </div>
          </div>
          <n-empty v-else :description="statsLoadState === 'error' ? '项目排行待确认，请稍后重试。' : '暂无项目数据'" style="padding: 20px 0;" />
        </n-card>
      </n-col>

      <n-col :span="10">
        <n-card class="glass-card" :bordered="false">
          <template #header>
            <div class="section-header">
              <span>最近活动</span>
              <small>最近 8 条任务与额度变更</small>
            </div>
          </template>
          <div v-if="recentActivities.length > 0" class="activity-list">
            <div v-for="item in recentActivities" :key="`${item.type}-${item.title}-${item.createdAt}`" class="activity-item">
              <div class="activity-dot" :class="activityTypeClass(item)"></div>
              <div class="activity-body">
                <div class="activity-top">
                  <strong>{{ item.title }}</strong>
                  <span>{{ formatDateTime(item.createdAt) }}</span>
                </div>
                <p>{{ item.description }}</p>
              </div>
            </div>
          </div>
          <n-empty v-else :description="statsLoadState === 'error' ? '近期活动待确认，请稍后重试。' : '暂无近期活动'" style="padding: 20px 0;" />
        </n-card>
      </n-col>
    </n-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import request, { clearCache } from '@/api/request'
import {
  type StatsActivity,
  type StatsDashboard,
  type StatsDistribution,
  type StatsOverview,
  type StatsProjectRanking,
  type StatsTrendPoint
} from '@/api/stats'
import { useProjectStore } from '@/store/project'

type ScopeMode = 'all' | 'current'

const projectStore = useProjectStore()

const loading = ref(false)
const statsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const scopeMode = ref<ScopeMode>('all')
const dashboard = ref<StatsDashboard>(createEmptyDashboard())
const suppressAutoReload = ref(false)
let refreshTimer: number | null = null
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

const scopeOptions = [
  { label: '全部项目', value: 'all' },
  { label: '当前项目', value: 'current' }
]

const selectedProjectId = computed(() => {
  if (scopeMode.value === 'current' && projectStore.activeProjectId) {
    return projectStore.activeProjectId
  }
  return undefined
})

const currentProjectName = computed(() => {
  if (!selectedProjectId.value) {
    return ''
  }
  return projectStore.projects.find(item => item.id === selectedProjectId.value)?.name || ''
})

const scopeDetailLabel = computed(() => {
  if (scopeMode.value === 'current' && currentProjectName.value) {
    return currentProjectName.value
  }
  if (scopeMode.value === 'current') {
    return '当前项目'
  }
  return '全部项目'
})

const overview = computed<StatsOverview>(() => dashboard.value.overview)
const distribution = computed<StatsDistribution>(() => dashboard.value.distribution)
const trends = computed<StatsTrendPoint[]>(() => dashboard.value.trends || [])
const projectRankings = computed<StatsProjectRanking[]>(() => dashboard.value.projectRankings || [])
const recentActivities = computed<StatsActivity[]>(() => dashboard.value.recentActivities || [])

const failedTaskCount = computed(() => toOptionalNumber(distribution.value.failedTaskCount))
const successRate = computed(() => {
  const total = toOptionalNumber(overview.value.taskCount)
  const success = toOptionalNumber(distribution.value.successTaskCount)
  if (total === null || success === null) return null
  return total > 0 ? (success / total) * 100 : 0
})
const imageTaskPercent = computed(() => {
  const total = toOptionalNumber(overview.value.taskCount)
  const image = toOptionalNumber(distribution.value.imageTaskCount)
  if (total === null || image === null) return null
  return total > 0 ? (image / total) * 100 : 0
})
const videoTaskPercent = computed(() => {
  const total = toOptionalNumber(overview.value.taskCount)
  const video = toOptionalNumber(distribution.value.videoTaskCount)
  if (total === null || video === null) return null
  return total > 0 ? (video / total) * 100 : 0
})
const quotaPercent = computed(() => {
  const limit = toOptionalNumber(overview.value.quotaLimit)
  const used = toOptionalNumber(overview.value.quotaUsed)
  if (limit === null || used === null) return null
  return limit > 0 ? (used / limit) * 100 : 0
})

const summaryCards = computed(() => [
  {
    key: 'tasks',
    label: '任务总量',
    value: formatOptionalNumber(overview.value.taskCount),
    hint: toOptionalNumber(overview.value.taskCount) === null
      ? `${scopeDetailLabel.value}内的任务总量待确认`
      : `${scopeDetailLabel.value}内共发起 ${overview.value.taskCount} 次生成`,
    color: 'green'
  },
  {
    key: 'success-rate',
    label: '成功率',
    value: formatOptionalPercent(successRate.value, 1),
    hint: toOptionalNumber(distribution.value.successTaskCount) === null || failedTaskCount.value === null
      ? '任务成功/失败数据待确认'
      : `${distribution.value.successTaskCount} 成功 / ${failedTaskCount.value} 失败`,
    color: 'blue'
  },
  {
    key: 'assets',
    label: '资产沉淀',
    value: formatOptionalNumber(overview.value.assetCount),
    hint: toOptionalNumber(overview.value.favoriteAssetCount) === null
      ? '收藏资产数量待确认'
      : `其中收藏 ${overview.value.favoriteAssetCount} 项`,
    color: 'purple'
  },
  {
    key: 'quota',
    label: '额度使用率',
    value: formatOptionalPercent(quotaPercent.value, 1),
    hint: toOptionalNumber(overview.value.quotaUsed) === null || toOptionalNumber(overview.value.quotaLimit) === null
      ? '额度消耗数据待确认'
      : `已消耗 ${overview.value.quotaUsed} / ${overview.value.quotaLimit}`,
    color: 'amber'
  }
])

const trendDays = computed(() => {
  const total = Math.max(trends.value.length, 14)
  const step = total > 1 ? 620 / (total - 1) : 0
  return trends.value.map((item, index) => ({
    key: `${item.date}-${index}`,
    label: item.date,
    x: 80 + index * step,
    taskCount: toOptionalNumber(item.taskCount),
    quotaUsed: toOptionalNumber(item.quotaUsed)
  }))
})

const maxTaskDaily = computed(() => {
  const values = trendDays.value
    .map(item => item.taskCount)
    .filter((value): value is number => value !== null)
  return values.length > 0 ? Math.max(1, ...values) : 1
})
const maxQuotaDaily = computed(() => {
  const values = trendDays.value
    .map(item => item.quotaUsed)
    .filter((value): value is number => value !== null)
  return values.length > 0 ? Math.max(1, ...values) : 1
})

const taskTrendPoints = computed(() => {
  return trendDays.value
    .map((item, index) => {
      if (item.taskCount === null) return null
      return {
        key: `task-${index}`,
        x: item.x,
        y: 220 - (item.taskCount / maxTaskDaily.value) * 150
      }
    })
    .filter((item): item is { key: string, x: number, y: number } => item !== null)
})

const taskTrendPath = computed(() => {
  if (taskTrendPoints.value.length === 0) return ''
  return taskTrendPoints.value
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ')
})

const taskTrendAreaPath = computed(() => {
  if (taskTrendPoints.value.length === 0) return ''
  const first = taskTrendPoints.value[0]
  const last = taskTrendPoints.value[taskTrendPoints.value.length - 1]
  return `${taskTrendPath.value} L ${last.x} 220 L ${first.x} 220 Z`
})

const quotaBars = computed(() => {
  return trendDays.value
    .map((item, index) => {
      if (item.quotaUsed === null) return null
      const height = (item.quotaUsed / maxQuotaDaily.value) * 120
      return {
        key: `quota-${index}`,
        x: item.x,
        y: 220 - height,
        height
      }
    })
    .filter((item): item is { key: string, x: number, y: number, height: number } => item !== null)
})

const trendGridLines = computed(() => [0, 1, 2, 3].map(item => ({ key: `grid-${item}`, y: 70 + item * 50 })))
const xAxisLabels = computed(() => trendDays.value.filter((_, index) => index % 2 === 0 || index === trendDays.value.length - 1))
const yAxisTicks = computed(() => [
  { key: 'zero', y: 220, label: '0' },
  { key: 'mid', y: 145, label: `${Math.round(maxTaskDaily.value / 2)}` },
  { key: 'max', y: 70, label: `${maxTaskDaily.value}` }
])

const typeCircumference = 2 * Math.PI * 64
const typeImageLength = computed(() => (typeCircumference * (imageTaskPercent.value ?? 0)) / 100)
const typeVideoLength = computed(() => (typeCircumference * (videoTaskPercent.value ?? 0)) / 100)
const typeImageDash = computed(() => `${typeImageLength.value} ${typeCircumference - typeImageLength.value}`)
const typeVideoDash = computed(() => `${typeVideoLength.value} ${typeCircumference - typeVideoLength.value}`)

const statusCards = computed(() => {
  const total = toOptionalNumber(overview.value.taskCount)
  const success = toOptionalNumber(distribution.value.successTaskCount)
  const running = toOptionalNumber(distribution.value.runningTaskCount)
  const failed = toOptionalNumber(distribution.value.failedTaskCount)
  return [
    {
      key: 'success',
      label: '成功',
      value: success,
      percent: total === null || success === null ? null : (total > 0 ? (success / Math.max(total, 1)) * 100 : 0),
      color: '#10b981'
    },
    {
      key: 'running',
      label: '进行中',
      value: running,
      percent: total === null || running === null ? null : (total > 0 ? (running / Math.max(total, 1)) * 100 : 0),
      color: '#3b82f6'
    },
    {
      key: 'failed',
      label: '失败',
      value: failed,
      percent: total === null || failed === null ? null : (total > 0 ? (failed / Math.max(total, 1)) * 100 : 0),
      color: '#ef4444'
    }
  ]
})

function createEmptyDashboard(): StatsDashboard {
  return {
    overview: {
      projectCount: 0,
      taskCount: null,
      successTaskCount: null,
      assetCount: null,
      favoriteAssetCount: null,
      quotaUsed: null,
      quotaLimit: null
    },
    distribution: {
      imageTaskCount: null,
      videoTaskCount: null,
      successTaskCount: null,
      runningTaskCount: null,
      failedTaskCount: null
    },
    trends: [],
    projectRankings: [],
    recentActivities: []
  }
}

function normalizeDateTime(value?: string) {
  return String(value || '').replace('T', ' ').substring(0, 19)
}

function isPlainObject(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function toOptionalNumber(value: unknown): number | null {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function requireNonNegativeNumber(value: unknown, errorMessage: string) {
  const normalized = toOptionalNumber(value)
  if (normalized == null || normalized < 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function formatOptionalNumber(value: unknown) {
  const num = toOptionalNumber(value)
  return num === null ? '-' : `${num}`
}

function formatOptionalPercent(value?: number | null, digits = 1) {
  return value == null ? '-' : `${value.toFixed(digits)}%`
}

function formatDateTime(value?: string) {
  const normalized = normalizeDateTime(value)
  return normalized ? normalized.substring(5, 16) : '--'
}

function activityTypeClass(item: StatsActivity) {
  return item.status === 'failed' ? 'error' : item.type
}

function normalizeOverview(value: unknown): StatsOverview {
  if (!isPlainObject(value)) {
    throw new Error('统计数据待确认')
  }
  return {
    projectCount: requireNonNegativeNumber(value.projectCount, '统计数据待确认'),
    taskCount: requireNonNegativeNumber(value.taskCount, '统计数据待确认'),
    successTaskCount: requireNonNegativeNumber(value.successTaskCount, '统计数据待确认'),
    assetCount: requireNonNegativeNumber(value.assetCount, '统计数据待确认'),
    favoriteAssetCount: requireNonNegativeNumber(value.favoriteAssetCount, '统计数据待确认'),
    quotaUsed: requireNonNegativeNumber(value.quotaUsed, '统计数据待确认'),
    quotaLimit: requireNonNegativeNumber(value.quotaLimit, '统计数据待确认')
  }
}

function normalizeDistribution(value: unknown, overviewValue: StatsOverview): StatsDistribution {
  if (!isPlainObject(value)) {
    throw new Error('统计数据待确认')
  }
  const distribution = {
    imageTaskCount: requireNonNegativeNumber(value.imageTaskCount, '统计数据待确认'),
    videoTaskCount: requireNonNegativeNumber(value.videoTaskCount, '统计数据待确认'),
    successTaskCount: requireNonNegativeNumber(value.successTaskCount, '统计数据待确认'),
    runningTaskCount: requireNonNegativeNumber(value.runningTaskCount, '统计数据待确认'),
    failedTaskCount: requireNonNegativeNumber(value.failedTaskCount, '统计数据待确认')
  }
  const taskCount = overviewValue.taskCount ?? 0
  if (
    distribution.imageTaskCount + distribution.videoTaskCount > taskCount
    || distribution.successTaskCount + distribution.runningTaskCount + distribution.failedTaskCount > taskCount
    || distribution.successTaskCount !== overviewValue.successTaskCount
  ) {
    throw new Error('统计数据待确认')
  }
  return distribution
}

function normalizeTrends(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('统计数据待确认')
  }
  const normalized = value.map((item) => {
    if (!isPlainObject(item)) {
      throw new Error('统计数据待确认')
    }
    const date = normalizeOptionalText(item.date)
    const taskCount = requireNonNegativeNumber(item.taskCount, '统计数据待确认')
    const quotaUsed = requireNonNegativeNumber(item.quotaUsed, '统计数据待确认')
    if (!date) {
      throw new Error('统计数据待确认')
    }
    return { date, taskCount, quotaUsed }
  })
  const dates = new Set<string>()
  normalized.forEach(item => {
    if (dates.has(item.date)) {
      throw new Error('统计数据待确认')
    }
    dates.add(item.date)
  })
  return normalized
}

function normalizeProjectRankings(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('统计数据待确认')
  }
  const normalized = value.map((item) => {
    if (!isPlainObject(item)) {
      throw new Error('统计数据待确认')
    }
    const projectId = requireNonNegativeNumber(item.projectId, '统计数据待确认')
    const rank = requireNonNegativeNumber(item.rank, '统计数据待确认')
    const name = normalizeOptionalText(item.name)
    const taskCount = requireNonNegativeNumber(item.taskCount, '统计数据待确认')
    const successTaskCount = requireNonNegativeNumber(item.successTaskCount, '统计数据待确认')
    const successRate = requireNonNegativeNumber(item.successRate, '统计数据待确认')
    const assetCount = requireNonNegativeNumber(item.assetCount, '统计数据待确认')
    const quotaUsed = requireNonNegativeNumber(item.quotaUsed, '统计数据待确认')
    const weightPercent = requireNonNegativeNumber(item.weightPercent, '统计数据待确认')
    if (
      projectId <= 0
      || rank <= 0
      || !name
      || successTaskCount > taskCount
      || successRate > 100
      || weightPercent > 100
    ) {
      throw new Error('统计数据待确认')
    }
    return {
      rank,
      projectId,
      name,
      description: normalizeOptionalText(item.description),
      taskCount,
      successTaskCount,
      successRate,
      assetCount,
      quotaUsed,
      weightPercent,
      lastActiveAt: normalizeDateTime(normalizeOptionalText(item.lastActiveAt))
    }
  })
  const projectIds = new Set<number>()
  const ranks = new Set<number>()
  normalized.forEach(item => {
    if (projectIds.has(item.projectId) || ranks.has(item.rank)) {
      throw new Error('统计数据待确认')
    }
    projectIds.add(item.projectId)
    ranks.add(item.rank)
  })
  return normalized
}

function normalizeRecentActivities(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('统计数据待确认')
  }
  return value.map((item) => {
    if (!isPlainObject(item)) {
      throw new Error('统计数据待确认')
    }
    const type = normalizeOptionalText(item.type)
    const title = normalizeOptionalText(item.title)
    const description = normalizeOptionalText(item.description)
    const status = normalizeOptionalText(item.status)
    if (!type || !title || !description || !status) {
      throw new Error('统计数据待确认')
    }
    return {
      type,
      title,
      description,
      status,
      createdAt: normalizeDateTime(normalizeOptionalText(item.createdAt))
    }
  })
}

function normalizeDashboard(data: unknown): StatsDashboard {
  if (!isPlainObject(data)) {
    throw new Error('统计数据待确认')
  }
  const overviewValue = normalizeOverview(data.overview)
  return {
    overview: overviewValue,
    distribution: normalizeDistribution(data.distribution, overviewValue),
    trends: normalizeTrends(data.trends),
    projectRankings: normalizeProjectRankings(data.projectRankings),
    recentActivities: normalizeRecentActivities(data.recentActivities)
  }
}

async function loadStatsData() {
  loading.value = true
  statsLoadState.value = 'loading'
  try {
    clearCache('stats/dashboard')
    clearCache('tasks')
    clearCache('assets')
    try {
      await projectStore.refresh()
    } catch (projectErr) {
      console.error(projectErr)
    }
    const res = await request.get('/api/stats/dashboard', {
      params: { projectId: selectedProjectId.value },
      headers: NO_CACHE_HEADERS
    })
    dashboard.value = normalizeDashboard(res.data)
    statsLoadState.value = 'ready'
  } catch (err: any) {
    dashboard.value = createEmptyDashboard()
    statsLoadState.value = 'error'
    console.error(err)
  } finally {
    loading.value = false
  }
}

async function refreshPageData() {
  loading.value = true
  statsLoadState.value = 'loading'
  try {
    suppressAutoReload.value = true
    try {
      await projectStore.refresh()
    } catch (projectErr) {
      console.error(projectErr)
    }
    clearCache('stats/dashboard')
    const res = await request.get('/api/stats/dashboard', {
      params: { projectId: selectedProjectId.value },
      headers: NO_CACHE_HEADERS
    })
    dashboard.value = normalizeDashboard(res.data)
    statsLoadState.value = 'ready'
  } catch (err: any) {
    dashboard.value = createEmptyDashboard()
    statsLoadState.value = 'error'
    console.error(err)
  } finally {
    suppressAutoReload.value = false
    loading.value = false
  }
}

watch([scopeMode, () => projectStore.activeProjectId], () => {
  if (suppressAutoReload.value) {
    return
  }
  void loadStatsData()
})

onMounted(async () => {
  try {
    await projectStore.refresh()
  } catch (err) {
    console.error(err)
  }
  await loadStatsData()
  // 每 15 秒自动刷新，让数据保持最新
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      clearCache('stats/dashboard')
      void loadStatsData()
    }
  }, 15000)
})

onUnmounted(() => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.stats-container {
  padding-bottom: 40px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.page-header h2 {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 6px 0;
  color: var(--text-primary);
}

.subtitle {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.status-note {
  margin-bottom: 16px;
  font-size: 12px;
  color: #fca5a5;
}

.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  backdrop-filter: blur(16px);
  border: 1px solid var(--border-color) !important;
  border-radius: 16px !important;
}

.stats-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stats-empty-box {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.summary-label {
  font-size: 12px;
  color: var(--text-muted);
}

.summary-value {
  font-size: 28px;
  line-height: 1;
  color: var(--text-primary);
}

.summary-value.green { color: #34d399; }
.summary-value.blue { color: #60a5fa; }
.summary-value.purple { color: #c084fc; }
.summary-value.amber { color: #fbbf24; }

.summary-hint {
  font-size: 11px;
  color: #7c8aa5;
  line-height: 1.5;
}

.section-grid {
  margin-top: 20px;
}

.section-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.section-header small {
  font-size: 11px;
  color: #94a3b8;
  font-weight: 400;
}

.trend-chart-box {
  width: 100%;
  min-height: 280px;
}

.trend-chart {
  width: 100%;
  height: 280px;
}

.chart-grid {
  stroke: rgba(128, 128, 128, 0.1);
  stroke-dasharray: 4;
}

.trend-line {
  fill: none;
  stroke: #10b981;
  stroke-width: 3;
  filter: drop-shadow(0 0 8px rgba(16, 185, 129, 0.35));
}

.trend-dot {
  fill: #ffffff;
  stroke: #10b981;
  stroke-width: 2;
}

.axis-label {
  fill: #6b7280;
  font-size: 10px;
}

.chart-legend-row,
.legend-col {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.legend-col {
  flex-direction: column;
  align-items: flex-start;
  margin-top: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #9ca3af;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.legend-dot.green { background: #10b981; }
.legend-dot.amber { background: #f59e0b; }

.distribution-box {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
}

.donut-total {
  fill: var(--text-primary);
  font-size: 28px;
  font-weight: 800;
}

.donut-label {
  fill: #94a3b8;
  font-size: 11px;
}

.status-card {
  margin-top: 20px;
}

.status-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.status-name {
  font-size: 12px;
  color: var(--text-secondary);
}

.status-value {
  font-size: 12px;
  color: var(--text-muted);
}

.project-ranking-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.project-ranking-item {
  padding: 12px;
  border-radius: 12px;
  background: rgba(128, 128, 128, 0.03);
  border: 1px solid var(--border-color);
}

.project-row-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}

.project-title {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.rank-badge {
  min-width: 24px;
  height: 24px;
  padding: 0 6px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(16, 185, 129, 0.18);
  color: #34d399;
  font-size: 12px;
  font-weight: 700;
}

.project-title strong {
  display: block;
  font-size: 14px;
  color: var(--text-primary);
}

.project-title small,
.project-last-active {
  font-size: 11px;
  color: #94a3b8;
}

.project-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #cbd5e1;
  margin-bottom: 10px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.activity-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.activity-dot.image { background: #10b981; }
.activity-dot.video { background: #f59e0b; }
.activity-dot.error { background: #ef4444; }
.activity-dot.quota { background: #3b82f6; }

.activity-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.activity-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.activity-top strong {
  font-size: 13px;
  color: var(--text-primary);
}

.activity-top span {
  font-size: 11px;
  color: #94a3b8;
}

.activity-body p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: #94a3b8;
}

@media (max-width: 1200px) {
  .stats-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .distribution-box {
    flex-direction: column;
  }
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
  }

  .stats-summary-grid {
    grid-template-columns: 1fr;
  }

  .project-row-top,
  .activity-top {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
