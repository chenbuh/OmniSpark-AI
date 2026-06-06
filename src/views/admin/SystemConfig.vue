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
import { collectAllPageRecords } from '@/api/pagination'
import request from '@/api/request'
import { usePlatformStore } from '@/store/platform'

type HealthStatus = 'UP' | 'DOWN' | 'DEGRADED'
type ServiceHealthStatus = 'UP' | 'DOWN'

interface SystemConfigItem {
  id: number
  configKey: string
  configValue: string
  configGroup: string
}

interface SystemHealth {
  status: HealthStatus
  database: ServiceHealthStatus
  redis: ServiceHealthStatus
  version: string
  uptimeReadable: string
  startedAt: string
}

const message = useMessage()
const platformStore = usePlatformStore()
const loadingConfigs = ref(true)
const configs = ref<SystemConfigItem[] | null>(null)
const health = ref<SystemHealth | null>(null)
const editingId = ref<number | null>(null)
const editValue = ref('')
const configsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const healthLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }
const HEALTH_STATUSES: HealthStatus[] = ['UP', 'DOWN', 'DEGRADED']
const SERVICE_HEALTH_STATUSES: ServiceHealthStatus[] = ['UP', 'DOWN']

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}

function normalizeHealthStatus(value: unknown): HealthStatus {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!HEALTH_STATUSES.includes(normalized as HealthStatus)) {
    throw new Error('系统健康状态待确认')
  }
  return normalized as HealthStatus
}

function normalizeServiceHealthStatus(value: unknown): ServiceHealthStatus {
  const normalized = typeof value === 'string' ? value.trim() : ''
  if (!SERVICE_HEALTH_STATUSES.includes(normalized as ServiceHealthStatus)) {
    throw new Error('系统健康状态待确认')
  }
  return normalized as ServiceHealthStatus
}

function normalizeConfigItem(item: unknown): SystemConfigItem {
  if (!isPlainObject(item)) {
    throw new Error('系统配置待确认')
  }
  const id = Number(item.id)
  const configKey = typeof item.configKey === 'string' ? item.configKey.trim() : ''
  const configValue = typeof item.configValue === 'string' ? item.configValue : ''
  const configGroup = typeof item.configGroup === 'string' ? item.configGroup.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !configKey || !configGroup) {
    throw new Error('系统配置待确认')
  }
  return {
    id,
    configKey,
    configValue,
    configGroup
  }
}

function normalizeConfigList(value: unknown): SystemConfigItem[] {
  if (!Array.isArray(value)) {
    throw new Error('系统配置待确认')
  }
  const normalized = value.map((item: unknown) => normalizeConfigItem(item))
  const ids = new Set<number>()
  const configKeys = new Set<string>()
  for (const item of normalized) {
    if (ids.has(item.id) || configKeys.has(item.configKey)) {
      throw new Error('系统配置待确认')
    }
    ids.add(item.id)
    configKeys.add(item.configKey)
  }
  return normalized
}

function requireHealthStatus(value: unknown): SystemHealth {
  if (!isPlainObject(value)) {
    throw new Error('系统健康状态待确认')
  }
  const version = typeof value.version === 'string' ? value.version.trim() : ''
  if (!version) {
    throw new Error('系统健康状态待确认')
  }
  return {
    status: normalizeHealthStatus(value.status),
    database: normalizeServiceHealthStatus(value.database),
    redis: normalizeServiceHealthStatus(value.redis),
    version,
    uptimeReadable: typeof value.uptimeReadable === 'string' ? value.uptimeReadable.trim() : '',
    startedAt: typeof value.startedAt === 'string' ? value.startedAt.trim() : ''
  }
}

async function loadConfigs() {
  loadingConfigs.value = true
  configsLoadState.value = 'loading'
  try {
    const allConfigs = await collectAllPageRecords<SystemConfigItem>({
      loadPage: (page, pageSize) => request.get<unknown>('/api/admin/config/page', {
        params: { page, pageSize },
        headers: NO_CACHE_HEADERS
      }),
      errorMessage: '系统配置待确认'
    })
    configs.value = normalizeConfigList(allConfigs)
    const platformNameConfig = configs.value.find(item => item.configKey === 'platform.name')
    if (platformNameConfig?.configValue) {
      platformStore.setPlatformName(platformNameConfig.configValue)
    }
    configsLoadState.value = 'ready'
  } catch (err: unknown) {
    configs.value = null
    configsLoadState.value = 'error'
    message.error(getErrorMessage(err, '加载系统配置失败'))
  } finally {
    loadingConfigs.value = false
  }
}

async function loadHealth() {
  healthLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/health')
    health.value = requireHealthStatus(getResponseData(response, '系统健康状态待确认'))
    healthLoadState.value = 'ready'
  } catch (err: unknown) {
    health.value = null
    healthLoadState.value = 'error'
    message.error(getErrorMessage(err, '加载系统健康状态失败'))
  }
}

onMounted(() => { loadConfigs(); loadHealth() })

function startEdit(cfg: SystemConfigItem) {
  editingId.value = cfg.id
  editValue.value = cfg.configValue
}

async function handleSave(id: number) {
  try {
    const previousCount = configs.value?.length
    const currentConfig = configs.value?.find(item => item.id === id)
    const nextValue = editValue.value
    await request.put(`/api/admin/config/${id}?value=${encodeURIComponent(editValue.value)}`)
    await loadConfigs()
    const confirmed = configs.value?.find(item => item.id === id)
    if (
      !confirmed
      || confirmed.configValue !== nextValue
      || (currentConfig && (confirmed.configKey !== currentConfig.configKey || confirmed.configGroup !== currentConfig.configGroup))
      || (typeof previousCount === 'number' && configs.value?.length !== previousCount)
    ) {
      throw new Error('配置更新结果待确认')
    }
    if (confirmed.configKey === 'platform.name') {
      platformStore.setPlatformName(confirmed.configValue)
    }
    editingId.value = null
    message.success('配置已更新')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '更新失败'))
  }
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
