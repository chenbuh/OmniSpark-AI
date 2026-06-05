<template>
  <div class="admin-config">
    <div class="page-header">
      <h2>系统配置 (System Config)</h2>
      <p class="subtitle">管理系统运行参数和平台设置。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <n-table :single-line="false" class="config-table">
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
          <tr v-for="cfg in configs" :key="cfg.id">
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
        </tbody>
      </n-table>
    </n-card>

    <!-- 系统状态 -->
    <n-card class="glass-card" :bordered="false" style="margin-top:20px;">
      <template #header><span style="font-weight:600;color:#e5e7eb;">系统状态</span></template>
      <n-descriptions v-if="health" :column="2" bordered>
        <n-descriptions-item label="状态">
          <n-tag :type="health.status === 'UP' ? 'success' : 'error'">{{ health.status }}</n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="数据库">{{ health.database }}</n-descriptions-item>
        <n-descriptions-item label="Redis">{{ health.redis }}</n-descriptions-item>
        <n-descriptions-item label="版本">{{ health.version }}</n-descriptions-item>
        <n-descriptions-item label="运行时长">{{ health.uptimeReadable || '-' }}</n-descriptions-item>
        <n-descriptions-item label="启动时间">{{ health.startedAt || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

const message = useMessage()
const configs = ref<any[]>([])
const health = ref<any>(null)
const editingId = ref<number | null>(null)
const editValue = ref('')

async function loadConfigs() {
  try {
    const res = await request.get('/api/admin/config')
    configs.value = (res as any).data || []
  } catch { configs.value = [] }
}

async function loadHealth() {
  try {
    const res = await request.get('/api/admin/health')
    health.value = (res as any).data
  } catch { health.value = null }
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
.config-table { background: transparent !important; }
.config-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.config-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 10px 8px; font-size: 13px; }
.cfg-val { font-size: 13px; color: #d1d5db; word-break: break-all; }
</style>
