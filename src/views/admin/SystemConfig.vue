<template>
  <div class="admin-config">
    <div class="page-header">
      <h2>系统配置 (System Config)</h2>
      <p class="subtitle">管理系统运行参数和平台设置。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div v-if="configsLoadState === 'error'" class="status-note">系统配置待确认，请稍后重试。</div>
      <div v-if="loadingConfigs && configs === null" class="loading-box">
        <n-spin size="small" />
      </div>
      <n-table v-else :single-line="false" class="config-table">
        <thead>
          <tr>
            <th style="width:80px">ID</th>
            <th style="width:160px">配置键</th>
            <th>配置值</th>
            <th style="width:120px">分组</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="cfg in configs || []" :key="cfg.id">
            <td><code>#{{ cfg.id }}</code></td>
            <td><code>{{ cfg.configKey }}</code></td>
            <td>
              <n-input v-if="editingId === cfg.id" v-model:value="editValue" size="small" />
              <span v-else class="cfg-val">{{ cfg.configValue }}</span>
            </td>
            <td><n-tag size="small">{{ cfg.configGroup }}</n-tag></td>
            <td>
              <n-button v-if="editingId === cfg.id" size="tiny" type="primary" @click="handleSave(cfg.id)">保存</n-button>
              <n-button v-else size="tiny" secondary @click="startEdit(cfg)">编辑</n-button>
            </td>
          </tr>
          <tr v-if="configs !== null && configs.length === 0">
            <td colspan="5" class="empty-cell">暂无系统配置</td>
          </tr>
          <tr v-else-if="configs === null">
            <td colspan="5" class="empty-cell">系统配置待确认，请稍后重试。</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 系统状态 -->
    <n-card class="glass-card" :bordered="false" style="margin-top:20px;">
      <template #header><span style="font-weight:600;color:#e5e7eb;">系统状态</span></template>
      <div v-if="healthLoadState === 'error'" class="status-note">系统健康状态待确认，请稍后重试。</div>
      <div v-else-if="healthLoadState === 'loading'" class="loading-box">
        <n-spin size="small" />
      </div>
      <n-descriptions v-else-if="health" :column="2" bordered>
        <n-descriptions-item label="状态">
          <n-tag :type="health.status === 'UP' ? 'success' : 'error'">{{ health.status }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="数据库">{{ health.database }}</n-descriptions-item>
        <n-descriptions-item label="Redis">{{ health.redis }}</n-descriptions-item>
        <n-descriptions-item label="版本">{{ health.version }}</n-descriptions-item>
        <n-descriptions-item label="运行时长">{{ health.uptimeReadable || '-' }}</n-descriptions-item>
        <n-descriptions-item label="启动时间">{{ health.startedAt || '-' }}</n-descriptions-item>
      </n-descriptions>
      <n-empty v-else description="系统健康状态待确认，请稍后重试。" style="padding:16px 0;" />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

const message = useMessage()
const loadingConfigs = ref(true)
const configs = ref<any[] | null>(null)
const health = ref<any>(null)
const editingId = ref<number | null>(null)
const editValue = ref('')
const configsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const healthLoadState = ref<'loading' | 'ready' | 'error'>('loading')

async function loadConfigs() {
  loadingConfigs.value = true
  configsLoadState.value = 'loading'
  try {
    const res = await request.get('/api/admin/config')
    const data = (res as any).data
    if (!Array.isArray(data)) {
      throw new Error('系统配置待确认')
    }
    configs.value = data
    configsLoadState.value = 'ready'
  } catch (err: any) {
    configs.value = null
    configsLoadState.value = 'error'
    message.error(err.message || '加载系统配置失败')
  } finally {
    loadingConfigs.value = false
  }
}

async function loadHealth() {
  healthLoadState.value = 'loading'
  try {
    const res = await request.get('/api/admin/health')
    const data = (res as any).data
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      throw new Error('系统健康状态待确认')
    }
    health.value = data
    healthLoadState.value = 'ready'
  } catch (err: any) {
    health.value = null
    healthLoadState.value = 'error'
    message.error(err.message || '加载系统健康状态失败')
  }
}

onMounted(() => { loadConfigs(); loadHealth() })

function startEdit(cfg: any) {
  editingId.value = cfg.id
  editValue.value = cfg.configValue
}

async function handleSave(id: number) {
  try {
    await request.put(`/api/admin/config/${id}?value=${encodeURIComponent(editValue.value)}`)
    message.success('配置已更新')
    editingId.value = null
    await loadConfigs()
  } catch { message.error('更新失败') }
}
</script>

<style scoped>
.admin-config { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #fca5a5; }
.config-table { background: transparent !important; }
.config-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.config-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 10px 8px; font-size: 13px; }
.cfg-val { font-size: 13px; color: #d1d5db; word-break: break-all; }
.empty-cell { text-align: center; padding: 30px !important; color: #9ca3af; }
</style>
