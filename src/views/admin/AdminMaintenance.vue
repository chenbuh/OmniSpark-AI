<template>
  <div class="admin-maintenance">
    <div class="page-header">
      <h2>维护模式 (Maintenance Mode)</h2>
      <p class="subtitle">开启后普通用户无法使用系统，仅管理员可访问。用于系统升级维护。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="status-bar">
        <n-tag :type="status.enabled ? 'error' : 'success'" size="large" round>
          {{ status.enabled ? '🛠 维护中' : '✅ 正常运行' }}
        </n-tag>
        <n-button :type="status.enabled ? 'success' : 'error'" @click="handleToggle">
          {{ status.enabled ? '关闭维护模式' : '开启维护模式' }}
        </n-button>
      </div>

      <n-divider />

      <n-form label-placement="top">
        <n-form-item label="维护提示消息">
          <n-input v-model:value="status.message" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="输入维护期间显示的提示消息..." />
        </n-form-item>
      </n-form>

      <n-button type="primary" @click="handleSaveMessage">保存消息</n-button>
    </n-card>

    <!-- 维护期间预览 -->
    <n-card v-if="status.enabled" class="glass-card" :bordered="false" style="margin-top:16px;">
      <template #header><span style="font-weight:600;color:#f59e0b;">用户端预览</span></template>
      <n-alert type="warning">
        <template #header>系统维护中</template>
        {{ status.message }}
      </n-alert>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import request from '@/api/request'

const message = useMessage()
const status = reactive({ enabled: false, message: '系统维护中，请稍后再试' })

onMounted(load)

async function load() {
  try {
    const res = await request.get('/api/admin/maintenance')
    const data = (res as any).data
    if (data) { status.enabled = data.enabled; status.message = data.message || status.message }
  } catch {}
}

async function handleToggle() {
  try {
    await request.post(`/api/admin/maintenance?enabled=${!status.enabled}&message=${encodeURIComponent(status.message)}`)
    status.enabled = !status.enabled
    message.success(status.enabled ? '维护模式已开启' : '维护模式已关闭')
  } catch { message.error('操作失败') }
}

async function handleSaveMessage() {
  try {
    await request.post(`/api/admin/maintenance?enabled=${status.enabled}&message=${encodeURIComponent(status.message)}`)
    message.success('消息已保存')
  } catch { message.error('保存失败') }
}
</script>

<style scoped>
.admin-maintenance { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.status-bar { display: flex; justify-content: space-between; align-items: center; }
</style>
