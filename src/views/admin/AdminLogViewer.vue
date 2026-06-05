<template>
  <div class="admin-logviewer">
    <div class="page-header">
      <h2>系统日志 (System Logs)</h2>
      <p class="subtitle">实时查看后端运行日志，用于故障排查和监控。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <n-space>
          <n-input v-model:value="search" placeholder="搜索日志关键字..." style="width:240px;" clearable>
            <template #prefix><Search class="s-icon" /></template>
          </n-input>
          <n-select v-model:value="lineCount" :options="lineOptions" style="width:100px;" />
        </n-space>
        <n-space>
          <n-button size="small" @click="loadLogs">刷新</n-button>
          <n-button size="small" secondary @click="autoRefresh = !autoRefresh">
            {{ autoRefresh ? '⏹ 停止自动' : '▶ 自动刷新' }}
          </n-button>
        </n-space>
      </div>

      <div v-if="loadingLogs && logs === null" class="loading-box">
        <n-spin size="small" />
      </div>

      <div v-else class="log-container" ref="logContainer">
        <div class="log-line" v-for="(line, i) in logs || []" :key="i">
          <span class="line-num">{{ i + 1 }}</span>
          <span class="line-text" :class="logLevelClass(line)">{{ line }}</span>
        </div>
        <div v-if="logs !== null && logs.length === 0" class="log-empty">暂无日志</div>
        <div v-else-if="logs === null" class="log-empty">日志数据待确认，请稍后重试。</div>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Search } from 'lucide-vue-next'
import request from '@/api/request'

const message = useMessage()
const loadingLogs = ref(true)
const logs = ref<string[] | null>(null)
const search = ref('')
const lineCount = ref(100)
const autoRefresh = ref(false)
const logContainer = ref<HTMLElement | null>(null)
let timer: any = null
let inFlight = false
let errorNotified = false
const POLL_INTERVAL_MS = 5000

const lineOptions = [
  { label: '50 行', value: 50 },
  { label: '100 行', value: 100 },
  { label: '200 行', value: 200 },
  { label: '500 行', value: 500 }
]

const logLevelClass = (line: string) => {
  if (line.includes('ERROR') || line.includes('FATAL')) return 'level-error'
  if (line.includes('WARN')) return 'level-warn'
  if (line.includes('INFO')) return 'level-info'
  return ''
}

async function loadLogs() {
  if (inFlight) return
  inFlight = true
  loadingLogs.value = true
  try {
    const params: Record<string, any> = { lines: lineCount.value }
    if (search.value) params.search = search.value
    const res = await request.get('/api/admin/logs', { params })
    if (!Array.isArray((res as any).data?.lines)) {
      throw new Error('日志数据待确认')
    }
    logs.value = (res as any).data.lines
    errorNotified = false
    // 滚动到底部
    setTimeout(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight
      }
    }, 50)
  } catch (err: any) {
    logs.value = null
    if (!errorNotified) {
      message.error(err.message || '日志加载失败')
      errorNotified = true
    }
  } finally {
    inFlight = false
    loadingLogs.value = false
  }
}

onMounted(() => { loadLogs() })

watchAutoRefresh()

function watchAutoRefresh() {
  onUnmounted(() => { if (timer) clearInterval(timer) })
  // We watch autoRefresh via a setter
}

watch(() => autoRefresh.value, (val) => {
  if (timer) { clearInterval(timer); timer = null }
  if (val) {
    timer = setInterval(() => {
      if (document.visibilityState === 'visible') {
        loadLogs()
      }
    }, POLL_INTERVAL_MS)
  }
})
</script>

<style scoped>
.admin-logviewer { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.s-icon { width: 16px; height: 16px; color: #9ca3af; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }

.log-container {
  height: 500px;
  overflow-y: auto;
  background: rgba(0,0,0,0.3);
  border-radius: 10px;
  padding: 12px;
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', monospace;
  font-size: 12px;
  line-height: 1.6;
}

.log-line {
  display: flex;
  gap: 12px;
  padding: 1px 0;
  border-bottom: 1px solid rgba(255,255,255,0.02);
}

.line-num {
  color: #4b5563;
  min-width: 40px;
  text-align: right;
  user-select: none;
  flex-shrink: 0;
}

.line-text {
  color: #d1d5db;
  white-space: pre-wrap;
  word-break: break-all;
}

.line-text.level-error { color: #ef4444; }
.line-text.level-warn { color: #f59e0b; }
.line-text.level-info { color: #10b981; }

.log-empty {
  text-align: center;
  padding: 40px;
  color: #6b7280;
}
</style>
