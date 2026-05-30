<template>
  <div class="admin-update">
    <div class="page-header">
      <h2>系统更新 (System Update)</h2>
      <p class="subtitle">查看系统版本信息并检查更新。</p>
    </div>

    <n-row :gutter="20">
      <!-- 当前版本 -->
      <n-col :span="12">
        <n-card title="当前版本" class="glass-card" :bordered="false">
          <n-descriptions :column="1">
            <n-descriptions-item label="版本号"><n-tag type="success">{{ version.currentVersion }}</n-tag></n-descriptions-item>
            <n-descriptions-item label="构建时间">{{ version.buildTime }}</n-descriptions-item>
            <n-descriptions-item label="服务器时间">{{ version.serverTime }}</n-descriptions-item>
            <n-descriptions-item label="Java 版本">{{ version.javaVersion }}</n-descriptions-item>
            <n-descriptions-item label="操作系统">{{ version.osName }} ({{ version.osArch }})</n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-col>

      <!-- 更新检查 -->
      <n-col :span="12">
        <n-card title="检查更新" class="glass-card" :bordered="false">
          <div v-if="!updateCheck" class="check-placeholder">
            <p>点击下方按钮检查是否有新版本可用。</p>
            <n-button type="primary" @click="checkUpdate" :loading="checking">
              <template #icon><RefreshCw /></template>检查更新
            </n-button>
          </div>

          <div v-else class="update-result">
            <n-descriptions :column="1">
              <n-descriptions-item label="当前版本">{{ updateCheck.currentVersion }}</n-descriptions-item>
              <n-descriptions-item label="最新版本">{{ updateCheck.latestVersion }}</n-descriptions-item>
              <n-descriptions-item label="状态">
                <n-tag v-if="updateCheck.hasUpdate" type="error">有新版本可用</n-tag>
                <n-tag v-else type="success">已是最新版本</n-tag>
              </n-descriptions-item>
            </n-descriptions>

            <n-alert v-if="updateCheck.hasUpdate" type="warning" style="margin-top:12px;">
              <template #header>新版本 {{ updateCheck.latestVersion }} 可用</template>
              <div style="font-size:12px;margin-bottom:8px;white-space:pre-wrap;">{{ updateCheck.releaseNotes }}</div>
              <n-button size="small" type="primary" @click="openReleaseUrl">前往下载</n-button>
            </n-alert>

            <n-alert v-else-if="updateCheck.error" type="info" style="margin-top:12px;">
              {{ updateCheck.error }}
            </n-alert>
          </div>
        </n-card>
      </n-col>
    </n-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import request from '@/api/request'

const version = ref<any>({})
const updateCheck = ref<any>(null)
const checking = ref(false)

async function loadVersion() {
  try { const res = await request.get('/api/admin/version'); version.value = (res as any).data || {} } catch {}
}

async function checkUpdate() {
  checking.value = true
  try {
    const res = await request.get('/api/admin/version/check')
    updateCheck.value = (res as any).data
  } catch { updateCheck.value = { latestVersion: '查询失败', hasUpdate: false, error: '网络错误' } }
  finally { checking.value = false }
}

function openReleaseUrl() {
  if (updateCheck.value?.releaseUrl) window.open(updateCheck.value.releaseUrl, '_blank')
}

loadVersion()
</script>

<style scoped>
.admin-update { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.check-placeholder { text-align: center; padding: 20px; color: #9ca3af; display: flex; flex-direction: column; align-items: center; gap: 16px; }
.update-result { display: flex; flex-direction: column; gap: 8px; }
</style>
