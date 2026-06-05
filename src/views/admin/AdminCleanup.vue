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

const message = useMessage()
const daysOld = ref(30)
const preview = ref<any>(null)
const result = ref<any>(null)
const previewing = ref(false)
const cleaning = ref(false)

const cleanupItems = [
  { key: 'oldTasks', label: '过期任务', color: '#f59e0b' },
  { key: 'oldAssets', label: '过期资产', color: '#8b5cf6' },
  { key: 'oldAuditLogs', label: '审计日志', color: '#3b82f6' },
  { key: 'oldLoginLogs', label: '登录日志', color: '#6b7280' }
]

const totalDeletable = computed(() => {
  if (!preview.value) return 0
  return cleanupItems.reduce((sum, item) => sum + (toOptionalNumber(preview.value[item.key]) ?? 0), 0)
})

async function handlePreview() {
  previewing.value = true
  result.value = null
  try {
    const res = await request.get('/api/admin/cleanup/preview', { params: { daysOld: daysOld.value } })
    preview.value = (res as any).data
  } catch { message.error('预览失败') }
  finally { previewing.value = false }
}

async function handleExecute() {
  cleaning.value = true
  try {
    const res = await request.delete('/api/admin/cleanup/execute', { params: { daysOld: daysOld.value } })
    result.value = (res as any).data
    preview.value = null
    message.success('清理完成！')
  } catch { message.error('清理失败') }
  finally { cleaning.value = false }
}

function displayCleanupMetric(source: any, key: string) {
  const normalized = toOptionalNumber(source?.[key])
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
</style>
