<template>
  <div class="admin-maintenance">
    <div class="page-header">
      <h2>维护模式 (Maintenance Mode)</h2>
      <p class="subtitle">开启后普通用户无法使用系统，仅管理员可访问。用于系统升级维护。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div v-if="statusLoadState === 'error'" class="status-note">维护模式状态待确认，请稍后重试。</div>
      <div class="status-bar">
        <n-tag :type="statusTagType" size="large" round>
          {{ statusTagLabel }}
        </n-tag>
        <n-button :type="status.enabled === true ? 'success' : 'error'" :disabled="loading" @click="handleToggle">
          {{ status.enabled === true ? '关闭维护模式' : '开启维护模式' }}
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
    <n-card v-if="status.enabled === true" class="glass-card" :bordered="false" style="margin-top:16px;">
      <template #header><span style="font-weight:600;color:#f59e0b;">用户端预览</span></template>
      <n-alert type="warning">
        <template #header>系统维护中</template>
        {{ status.message }}
      </n-alert>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { collectAllPageRecords } from '@/api/pagination'
import request from '@/api/request'

interface MaintenanceStatus {
  enabled: boolean
  message: string
}

interface MaintenanceConfigItem {
  id: number
  configKey: string
  configValue: string
  configGroup: string
}

const message = useMessage()
const loading = ref(true)
const status = reactive<{ enabled: boolean | null; message: string }>({ enabled: null, message: '' })
const statusLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

const statusTagType = computed(() => {
  if (statusLoadState.value === 'error') return 'warning'
  if (loading.value || status.enabled === null) return 'warning'
  return status.enabled ? 'error' : 'success'
})

const statusTagLabel = computed(() => {
  if (statusLoadState.value === 'error') return '状态待确认'
  if (loading.value || status.enabled === null) return '状态加载中'
  return status.enabled ? '🛠 维护中' : '✅ 正常运行'
})

onMounted(load)

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function normalizeMaintenanceStatus(value: unknown): MaintenanceStatus {
  if (!isPlainObject(value)) {
    throw new Error('维护模式状态待确认')
  }
  const enabled = value.enabled
  const message = value.message
  if (typeof enabled !== 'boolean' || typeof message !== 'string') {
    throw new Error('维护模式状态待确认')
  }
  return {
    enabled,
    message
  }
}

function normalizeConfigItem(value: unknown): MaintenanceConfigItem {
  if (!isPlainObject(value)) {
    throw new Error('维护模式配置待确认')
  }
  const id = Number(value.id)
  const configKey = typeof value.configKey === 'string' ? value.configKey.trim() : ''
  const configValue = typeof value.configValue === 'string' ? value.configValue : ''
  const configGroup = typeof value.configGroup === 'string' ? value.configGroup.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !configKey || !configGroup) {
    throw new Error('维护模式配置待确认')
  }
  return {
    id,
    configKey,
    configValue,
    configGroup
  }
}

function normalizeConfigList(value: unknown): MaintenanceConfigItem[] {
  if (!Array.isArray(value)) {
    throw new Error('维护模式配置待确认')
  }
  const normalized = value.map((item: unknown) => normalizeConfigItem(item))
  const seenIds = new Set<number>()
  const seenKeys = new Set<string>()
  normalized.forEach(item => {
    if (seenIds.has(item.id) || seenKeys.has(item.configKey)) {
      throw new Error('维护模式配置待确认')
    }
    seenIds.add(item.id)
    seenKeys.add(item.configKey)
  })
  return normalized
}

async function fetchMaintenanceStatus(noCache = false): Promise<MaintenanceStatus> {
  const res = await request.get<unknown>('/api/admin/maintenance', {
    headers: noCache ? NO_CACHE_HEADERS : undefined
  })
  return normalizeMaintenanceStatus(getResponseData(res, '维护模式状态待确认'))
}

async function fetchMaintenanceConfigs(): Promise<MaintenanceConfigItem[]> {
  const configs = await collectAllPageRecords<MaintenanceConfigItem>({
    loadPage: (page, pageSize) => request.get<unknown>('/api/admin/config/page', {
      params: { group: 'maintenance', page, pageSize },
      headers: NO_CACHE_HEADERS
    }),
    errorMessage: '维护模式配置待确认'
  })
  return normalizeConfigList(configs)
}

async function requireMaintenanceConfigConfirmed(expected: { enabled: boolean; message: string }) {
  const configs = await fetchMaintenanceConfigs()
  const modeConfig = configs.find(item => item.configKey === 'maintenance_mode')
  const messageConfig = configs.find(item => item.configKey === 'maintenance_message')
  if (
    !modeConfig
    || !messageConfig
    || modeConfig.configGroup !== 'maintenance'
    || messageConfig.configGroup !== 'maintenance'
    || modeConfig.configValue !== (expected.enabled ? 'true' : 'false')
    || messageConfig.configValue !== expected.message
  ) {
    throw new Error('维护模式更新结果待确认')
  }
}

async function load() {
  try {
    loading.value = true
    statusLoadState.value = 'loading'
    const data = await fetchMaintenanceStatus(true)
    status.enabled = data.enabled
    status.message = data.message
    statusLoadState.value = 'ready'
  } catch (err: unknown) {
    status.enabled = null
    status.message = ''
    statusLoadState.value = 'error'
    message.error(err instanceof Error && err.message ? err.message : '加载维护模式状态失败')
  } finally {
    loading.value = false
  }
}

async function handleToggle() {
  if (status.enabled === null) {
    message.error('维护模式状态尚未加载完成，请稍后重试')
    return
  }
  try {
    const nextEnabled = !status.enabled
    const nextMessage = status.message
    await request.post(`/api/admin/maintenance?enabled=${nextEnabled}&message=${encodeURIComponent(nextMessage)}`)
    await load()
    await requireMaintenanceConfigConfirmed({ enabled: nextEnabled, message: nextMessage })
    if (status.enabled !== nextEnabled || status.message !== nextMessage) {
      throw new Error('维护模式更新结果待确认')
    }
    message.success(status.enabled ? '维护模式已开启' : '维护模式已关闭')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '操作失败')
  }
}

async function handleSaveMessage() {
  if (status.enabled === null) {
    message.error('维护模式状态尚未加载完成，请稍后重试')
    return
  }
  try {
    const nextEnabled = status.enabled
    const nextMessage = status.message
    await request.post(`/api/admin/maintenance?enabled=${nextEnabled}&message=${encodeURIComponent(nextMessage)}`)
    await load()
    await requireMaintenanceConfigConfirmed({ enabled: nextEnabled, message: nextMessage })
    if (status.enabled !== nextEnabled || status.message !== nextMessage) {
      throw new Error('维护消息保存结果待确认')
    }
    message.success('消息已保存')
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '保存失败')
  }
}
</script>

<style scoped>
.admin-maintenance { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #fca5a5; }
.status-bar { display: flex; justify-content: space-between; align-items: center; }
</style>
