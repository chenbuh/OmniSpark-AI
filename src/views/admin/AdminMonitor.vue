<template>
  <div class="admin-monitor">
    <div class="page-header">
      <h2>性能监控 (System Monitor)</h2>
      <p class="subtitle">实时查看系统 CPU、内存、磁盘和 JVM 使用状态。</p>
      <n-button size="small" secondary @click="loadData" :loading="loading">刷新数据</n-button>
    </div>

    <n-row :gutter="16">
      <!-- CPU -->
      <n-col :span="8">
        <n-card class="glass-card monitor-card" :bordered="false">
          <template #header><span class="card-title">🖥 CPU</span></template>
          <div class="gauge-wrap">
            <svg viewBox="0 0 120 120" class="gauge">
              <circle cx="60" cy="60" r="50" fill="none" stroke="rgba(255,255,255,0.04)" stroke-width="10" />
              <circle cx="60" cy="60" r="50" fill="none" stroke="#3b82f6" stroke-width="10"
                :stroke-dasharray="circum" :stroke-dashoffset="gaugeOffset(data.processCpuUsage ?? 0)"
                transform="rotate(-90 60 60)" stroke-linecap="round" />
              <text x="60" y="56" text-anchor="middle" fill="#f3f4f6" font-size="22" font-weight="700">{{ formatPercent(data.processCpuUsage) }}</text>
              <text x="60" y="72" text-anchor="middle" fill="#6b7280" font-size="10">CPU</text>
            </svg>
            <div class="gauge-info">
              <div class="info-row"><span>系统负载</span><span>{{ data.cpu?.systemLoadAverage ?? '-' }}</span></div>
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
                :stroke-dasharray="circum" :stroke-dashoffset="gaugeOffset(data.memory?.heapUsagePercent ?? 0)"
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
          <tr v-for="disk in (data.disks || [])" :key="disk.path">
            <td><code>{{ disk.path }}</code></td>
            <td>
              <n-progress type="line" :percentage="disk.usagePercent" :height="8"
                :status="disk.usagePercent > 90 ? 'error' : disk.usagePercent > 70 ? 'warning' : 'success'" />
            </td>
            <td>{{ disk.usedReadable }}</td>
            <td>{{ disk.freeReadable }}</td>
            <td>{{ disk.totalReadable }}</td>
          </tr>
        </tbody>
      </n-table>
      <div class="disk-summary" v-if="data.diskTotal">
        总计: {{ formatBytes(data.diskTotal) }} · 已用: {{ formatBytes(data.diskUsed) }} ({{ formatPercent(data.diskUsagePercent) }}) · 可用: {{ formatBytes(data.diskFree) }}
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import request, { clearCache } from '@/api/request'

const message = useMessage()

const loading = ref(false)
const data = ref<any>({})
const circum = 2 * Math.PI * 50 // 314.16

let autoTimer: any = null
let errorNotified = false
onMounted(() => { loadData(); autoTimer = setInterval(loadData, 5000) })
onUnmounted(() => { if (autoTimer) clearInterval(autoTimer) })

async function loadData() {
  loading.value = true
  clearCache('monitor')
  try {
    const res = await request.get('/api/admin/monitor', { headers: { 'x-no-cache': '1' } })
    data.value = (res as any).data || {}
    errorNotified = false
  } catch (err: any) {
    // 5 秒轮询,仅首次失败提示,避免重复弹窗刷屏
    if (!errorNotified) {
      message.error(err.message || '加载监控数据失败')
      errorNotified = true
    }
  } finally { loading.value = false }
}

const gaugeOffset = (pct: number) => circum - (circum * Math.min(pct, 100) / 100)
const formatPercent = (v: any) => (v != null ? Math.round(v * 10) / 10 + '%' : '-')
const formatBytes = (v: any) => {
  if (!v) return '-'
  const b = Number(v)
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  if (b < 1024 * 1024 * 1024) return (b / (1024 * 1024)).toFixed(1) + ' MB'
  return (b / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}
</script>

<style scoped>
.admin-monitor { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; display: flex; justify-content: space-between; align-items: flex-end; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
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

.disk-summary { font-size: 12px; color: #9ca3af; padding: 8px 0 0; text-align: center; }
</style>
