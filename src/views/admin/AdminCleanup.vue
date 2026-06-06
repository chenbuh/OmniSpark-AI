<template>
  <div class="admin-cleanup">
    <div class="page-header">
      <h2>数据清理 (Data Cleanup)</h2>
      <p class="subtitle">清理过期的任务、资产和日志记录，释放数据库空间。</p>
    </div>

    <!-- 清理参数 -->
    <n-card class="glass-card" :bordered="false">
      <div class="param-row">
        <span class="param-label">清理超过</span>
        <n-input-number v-model:value="daysOld" :min="1" :max="365" :step="1" style="width:100px" />
        <span class="param-label">天的数据</span>
        <n-button type="primary" secondary @click="handlePreview" :loading="previewing">预览可清理量</n-button>
      </div>
      <div v-if="previewLoadState === 'error'" class="status-note">清理预览待确认，请稍后重试。</div>
      <div v-if="resultLoadState === 'error'" class="status-note">清理结果待确认，请稍后重试。</div>
    </n-card>

    <!-- 预览结果 -->
    <n-card v-if="preview" class="glass-card" :bordered="false" style="margin-top:16px;">
      <template #header><span style="font-weight:600;color:#e5e7eb;">可清理数据（{{ preview.daysOld }} 天前）</span></template>
      <n-row :gutter="16">
        <n-col :span="6" v-for="item in cleanupItems" :key="item.key">
          <n-card class="stats-card" :bordered="false">
            <div class="stats-inner">
              <span class="stats-label">{{ item.label }}</span>
              <span class="stats-value" :style="{ color: item.color }">{{ displayCleanupMetric(preview, item.key) }}</span>
              <span class="stats-unit">条记录</span>
            </div>
          </n-card>
        </n-col>
      </n-row>

      <n-alert v-if="totalDeletable > 0" type="warning" style="margin-top:16px;">
        <template #header>将删除 {{ totalDeletable }} 条记录，此操作不可撤销</template>
        <n-button type="error" @click="handleExecute" :loading="cleaning">确认清理</n-button>
      </n-alert>
      <n-alert v-else type="success" style="margin-top:16px;">没有需要清理的数据</n-alert>
    </n-card>

    <!-- 清理结果 -->
    <n-card v-if="result" class="glass-card" :bordered="false" style="margin-top:16px;">
      <template #header><span style="font-weight:600;color:#10b981;">清理完成</span></template>
      <n-descriptions :column="2">
        <n-descriptions-item v-for="item in cleanupItems" :key="item.key" :label="item.label">
          <span style="color:#10b981;font-weight:600;">已删除 {{ displayCleanupMetric(result, 'deleted' + item.key.charAt(0).toUpperCase() + item.key.slice(1)) }} 条</span>
        </n-descriptions-item>
      </n-descriptions>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

type CleanupMetricKey = 'oldTasks' | 'oldAssets' | 'oldAuditLogs' | 'oldLoginLogs'
type CleanupResultMetricKey = 'deletedOldTasks' | 'deletedOldAssets' | 'deletedOldAuditLogs' | 'deletedOldLoginLogs'

interface CleanupPreviewPayload {
  daysOld: number
  oldTasks: number
  oldAssets: number
  oldAuditLogs: number
  oldLoginLogs: number
}

interface CleanupResultPayload {
  deletedOldTasks: number
  deletedOldAssets: number
  deletedOldAuditLogs: number
  deletedOldLoginLogs: number
}

interface CleanupItem {
  key: CleanupMetricKey
  label: string
  color: string
}

const message = useMessage()
const daysOld = ref(30)
const preview = ref<CleanupPreviewPayload | null>(null)
const result = ref<CleanupResultPayload | null>(null)
const previewing = ref(false)
const cleaning = ref(false)
const previewLoadState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const resultLoadState = ref<'idle' | 'ready' | 'error'>('idle')
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

const cleanupItems: CleanupItem[] = [
  { key: 'oldTasks', label: '过期任务', color: '#f59e0b' },
  { key: 'oldAssets', label: '过期资产', color: '#8b5cf6' },
  { key: 'oldAuditLogs', label: '审计日志', color: '#3b82f6' },
  { key: 'oldLoginLogs', label: '登录日志', color: '#6b7280' }
]

const totalDeletable = computed(() => {
  const previewPayload = preview.value
  if (!previewPayload) return 0
  return cleanupItems.reduce((sum, item) => sum + (toOptionalNumber(previewPayload[item.key]) ?? 0), 0)
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

function normalizeCleanupPreview(payload: unknown): CleanupPreviewPayload {
  if (!isPlainObject(payload)) {
    throw new Error('清理预览待确认')
  }
  const daysOldValue = toOptionalNumber(payload.daysOld)
  const metrics = cleanupItems.reduce<Record<CleanupMetricKey, number>>((acc, item) => {
    const normalized = toOptionalNumber(payload[item.key])
    if (normalized == null || normalized < 0) {
      throw new Error('清理预览待确认')
    }
    acc[item.key] = normalized
    return acc
  }, {} as Record<CleanupMetricKey, number>)
  if (daysOldValue == null || daysOldValue < 1) {
    throw new Error('清理预览待确认')
  }
  return {
    daysOld: daysOldValue,
    ...metrics
  }
}

function normalizeCleanupResult(payload: unknown): CleanupResultPayload {
  if (!isPlainObject(payload)) {
    throw new Error('清理结果待确认')
  }
  const keys: CleanupResultMetricKey[] = cleanupItems.map(item => cleanupResultKey(item.key))
  return keys.reduce<CleanupResultPayload>((acc, key) => {
    const normalized = toOptionalNumber(payload[key])
    if (normalized == null || normalized < 0) {
      throw new Error('清理结果待确认')
    }
    acc[key] = normalized
    return acc
  }, {
    deletedOldTasks: 0,
    deletedOldAssets: 0,
    deletedOldAuditLogs: 0,
    deletedOldLoginLogs: 0
  })
}

function cleanupResultKey(metricKey: CleanupMetricKey): CleanupResultMetricKey {
  return `deleted${metricKey.charAt(0).toUpperCase()}${metricKey.slice(1)}` as CleanupResultMetricKey
}

function snapshotCleanupMetrics<T extends string>(source: Record<T, unknown> | null, keys: readonly T[]) {
  return Object.fromEntries(keys.map((key) => [key, source ? toOptionalNumber(source[key]) ?? 0 : 0])) as Record<T, number>
}

function requireCleanupExecutionConfirmed(
  resultPayload: CleanupResultPayload,
  previewSnapshot: Record<CleanupMetricKey, number> | null,
  confirmedPreviewSnapshot: Record<CleanupMetricKey, number> | null
) {
  const deletedMetricKeys = cleanupItems.map(item => cleanupResultKey(item.key))
  const deletedMetrics = snapshotCleanupMetrics(resultPayload as Record<CleanupResultMetricKey, unknown>, deletedMetricKeys)
  const deletedTotal = Object.values(deletedMetrics).reduce((sum, value) => sum + value, 0)
  if (previewSnapshot) {
    const previewTotal = Object.values(previewSnapshot).reduce((sum, value) => sum + value, 0)
    for (const item of cleanupItems) {
      const deletedValue = deletedMetrics[cleanupResultKey(item.key)]
      if (deletedValue > previewSnapshot[item.key]) {
        throw new Error('清理结果待确认')
      }
    }
    if (previewTotal > 0 && deletedTotal === 0) {
      throw new Error('清理结果待确认')
    }
  }
  if (previewSnapshot && confirmedPreviewSnapshot) {
    const previewTotal = Object.values(previewSnapshot).reduce((sum, value) => sum + value, 0)
    const confirmedTotal = Object.values(confirmedPreviewSnapshot).reduce((sum, value) => sum + value, 0)
    for (const item of cleanupItems) {
      const key = item.key
      const before = previewSnapshot[key]
      const after = confirmedPreviewSnapshot[key]
      const deletedValue = deletedMetrics[cleanupResultKey(key)]
      if (after > before) {
        throw new Error('清理结果待确认')
      }
      if (after > Math.max(0, before - deletedValue)) {
        throw new Error('清理结果待确认')
      }
      if (before > 0 && deletedValue === 0 && after >= before) {
        throw new Error('清理结果待确认')
      }
    }
    if (previewTotal > 0 && confirmedTotal >= previewTotal) {
      throw new Error('清理结果待确认')
    }
  }
}

async function fetchCleanupPreview(noCache = false) {
  const res = await request.get<unknown>('/api/admin/cleanup/preview', {
    params: { daysOld: daysOld.value },
    headers: noCache ? NO_CACHE_HEADERS : undefined
  })
  return normalizeCleanupPreview(getResponseData(res, '清理预览待确认'))
}

async function handlePreview() {
  previewing.value = true
  result.value = null
  resultLoadState.value = 'idle'
  try {
    previewLoadState.value = 'loading'
    preview.value = await fetchCleanupPreview(true)
    previewLoadState.value = 'ready'
  } catch (err: unknown) {
    preview.value = null
    previewLoadState.value = 'error'
    message.error(err instanceof Error && err.message ? err.message : '预览失败')
  }
  finally { previewing.value = false }
}

async function handleExecute() {
  cleaning.value = true
  try {
    const previewPayload = preview.value ?? await fetchCleanupPreview(true)
    const previewSnapshot = snapshotCleanupMetrics(previewPayload as Record<CleanupMetricKey, unknown>, cleanupItems.map(item => item.key))
    const res = await request.delete<unknown>('/api/admin/cleanup/execute', { params: { daysOld: daysOld.value } })
    const normalizedResult = normalizeCleanupResult(getResponseData(res, '清理结果待确认'))
    const confirmedPreview = await fetchCleanupPreview(true)
    const confirmedPreviewSnapshot = snapshotCleanupMetrics(confirmedPreview as Record<CleanupMetricKey, unknown>, cleanupItems.map(item => item.key))
    requireCleanupExecutionConfirmed(normalizedResult, previewSnapshot, confirmedPreviewSnapshot)
    result.value = normalizedResult
    resultLoadState.value = 'ready'
    preview.value = confirmedPreview
    previewLoadState.value = 'ready'
    message.success('清理完成！')
  } catch (err: unknown) {
    result.value = null
    resultLoadState.value = 'error'
    message.error(err instanceof Error && err.message ? err.message : '清理失败')
  }
  finally { cleaning.value = false }
}

function displayCleanupMetric(source: Record<string, unknown> | null, key: string) {
  const normalized = source ? toOptionalNumber(source[key]) : null
  return normalized == null ? '-' : normalized
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
.admin-cleanup { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.param-row { display: flex; align-items: center; gap: 12px; }
.param-label { font-size: 13px; color: #d1d5db; }
.stats-card { text-align: center; padding: 8px; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.04); border-radius: 12px; }
.stats-inner { display: flex; flex-direction: column; gap: 2px; }
.stats-label { font-size: 11px; color: #9ca3af; }
.stats-value { font-size: 24px; font-weight: 700; }
.stats-unit { font-size: 10px; color: #6b7280; }
.status-note { margin-top: 12px; font-size: 12px; color: #fca5a5; }
</style>
