<template>
  <div class="admin-webhooks">
    <div class="page-header">
      <h2>Webhook 管理 (Webhooks)</h2>
      <p class="subtitle">配置 Webhook，任务完成或失败时自动通知外部系统。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <span class="count">共 {{ listCountDisplay }} 个 Webhook</span>
        <n-button type="primary" @click="openCreateEditor">
          <template #icon><Plus /></template>新建
        </n-button>
      </div>
      <div v-if="eventOptionsLoadState === 'error'" class="status-note">Webhook 事件选项待确认，请稍后重试。</div>

      <div v-if="loadingList && list === null" class="loading-box">
        <n-spin size="small" />
      </div>
      <n-table v-else :single-line="false" class="admin-table">
        <thead>
          <tr><th style="width:60px">ID</th><th>名称</th><th>URL</th><th style="width:140px">事件</th><th style="width:80px">状态</th><th style="width:140px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="w in list || []" :key="w.id">
            <td><code>#{{ w.id }}</code></td>
            <td>{{ w.name }}</td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true" style="max-width:300px;">{{ w.url }}</n-ellipsis></td>
            <td><n-tag size="small">{{ eventLabels(w.events) }}</n-tag></td>
            <td>
              <n-tag v-if="normalizeBinaryStatus(w.status) === null" size="small" type="warning">状态待确认</n-tag>
              <n-switch v-else :value="normalizeBinaryStatus(w.status) === true" @update:value="toggleStatus(w)" />
            </td>
            <td>
              <n-space>
                <n-button size="tiny" secondary @click="editWebhook(w)">编辑</n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(w.id)">删除</n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="list !== null && list.length === 0">
            <td colspan="6" class="empty-cell">暂无 Webhook</td>
          </tr>
          <tr v-else-if="list === null">
            <td colspan="6" class="empty-cell">Webhook 数据待确认，请稍后重试。</td>
          </tr>
        </tbody>
      </n-table>
      <div class="pager" v-if="(total ?? 0) > pageSize">
        <n-pagination v-model:page="page" :page-size="pageSize" :item-count="total" @update:page="load" />
      </div>
    </n-card>

    <!-- 编辑弹窗 -->
    <n-modal v-model:show="showEditor" preset="card" :title="editingId ? '编辑 Webhook' : '新建 Webhook'" style="width:520px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="名称">
          <n-input v-model:value="form.name" placeholder="例如：通知服务器" />
        </n-form-item>
        <n-form-item label="回调 URL">
          <n-input v-model:value="form.url" placeholder="https://example.com/webhook" />
        </n-form-item>
        <n-form-item label="触发事件">
          <n-select v-model:value="form.events" :options="eventOptions" multiple :disabled="eventOptionsLoadState === 'error'" />
        </n-form-item>
        <div v-if="eventOptionsLoadState === 'error'" class="status-note modal-status">当前无法确认可用事件类型，请稍后重试。</div>
        <n-form-item label="密钥 (Secret)">
          <n-input v-model:value="form.secret" placeholder="可选，用于验证请求来源" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button type="primary" @click="handleSave">{{ editingId ? '更新' : '创建' }}</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Plus } from 'lucide-vue-next'
import request from '@/api/request'

interface WebhookRecord {
  id: number
  name: string
  url: string
  events: string[]
  status: boolean
  secret: string
}

interface WebhookEventOption {
  label: string
  value: string
}

const message = useMessage()
const loadingList = ref(true)
const list = ref<WebhookRecord[] | null>(null)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const eventOptions = ref<WebhookEventOption[]>([])
const eventOptionsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const listLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const page = ref(1)
const pageSize = 10
const total = ref<number | null>(null)
const form = reactive({ name: '', url: '', events: [] as string[], secret: '' })
const eventLabelMap = computed(() => Object.fromEntries(eventOptions.value.map(item => [item.value, item.label])))
const listCountDisplay = computed(() => total.value == null ? '-' : total.value)
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string): unknown {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function requireWebhook(value: unknown, action: 'create' | 'update'): WebhookRecord {
  if (!isPlainObject(value)) {
    throw new Error(action === 'create' ? 'Webhook 创建结果待确认' : 'Webhook 更新结果待确认')
  }
  const id = Number(value.id)
  const name = typeof value.name === 'string' ? value.name.trim() : ''
  const url = typeof value.url === 'string' ? value.url.trim() : ''
  const events = normalizeEvents(value.events)
  const status = normalizeBinaryStatus(value.status)
  if (!Number.isFinite(id) || id <= 0 || !name || !url || events.length === 0 || status === null) {
    throw new Error(action === 'create' ? 'Webhook 创建结果待确认' : 'Webhook 更新结果待确认')
  }
  return {
    id,
    name,
    url,
    events,
    status,
    secret: typeof value.secret === 'string' ? value.secret : ''
  }
}

function normalizeWebhookRecord(value: unknown): WebhookRecord {
  return requireWebhook(value, 'update')
}

function requireWebhookPage(value: unknown): { records: WebhookRecord[]; total: number } {
  if (!isPlainObject(value) || !Array.isArray(value.records)) {
    throw new Error('Webhook 数据待确认')
  }
  const totalValue = Number(value.total)
  if (!Number.isFinite(totalValue) || totalValue < 0) {
    throw new Error('Webhook 数据待确认')
  }
  const normalized = value.records.map((item: unknown) => normalizeWebhookRecord(item))
  const ids = new Set<number>()
  for (const item of normalized) {
    if (ids.has(item.id)) {
      throw new Error('Webhook 数据待确认')
    }
    ids.add(item.id)
  }
  if (normalized.length > totalValue) {
    throw new Error('Webhook 数据待确认')
  }
  return {
    records: normalized,
    total: totalValue
  }
}

onMounted(async () => {
  await loadEventOptions()
  await load()
})

async function load() {
  loadingList.value = true
  listLoadState.value = 'loading'
  try {
    const res = await request.get<unknown>('/api/admin/webhooks', {
      params: { page: page.value, pageSize },
      headers: NO_CACHE_HEADERS
    })
    const data = requireWebhookPage(getResponseData(res, 'Webhook 数据待确认'))
    list.value = data.records
    total.value = data.total
    listLoadState.value = 'ready'
  }
  catch (err: unknown) {
    list.value = null
    total.value = null
    listLoadState.value = 'error'
    message.error(err instanceof Error && err.message ? err.message : '加载 Webhook 列表失败')
  } finally {
    loadingList.value = false
  }
}

async function loadEventOptions() {
  eventOptionsLoadState.value = 'loading'
  try {
    const res = await request.get<unknown>('/api/admin/webhooks/meta', { headers: NO_CACHE_HEADERS })
    eventOptions.value = requireWebhookEventOptions(getResponseData(res, 'Webhook 事件待确认'))
    eventOptionsLoadState.value = 'ready'
    if (form.events.length === 0) {
      form.events = defaultEvents()
    }
  } catch {
    eventOptions.value = []
    eventOptionsLoadState.value = 'error'
  }
}

function defaultEvents() {
  const preferred = eventOptions.value.find(item => item.value === 'task.completed')?.value
  if (preferred) {
    return [preferred]
  }
  return eventOptions.value[0]?.value ? [eventOptions.value[0].value] : []
}

function resetForm() { Object.assign(form, { name: '', url: '', events: defaultEvents(), secret: '' }) }

function openCreateEditor() {
  editingId.value = null
  resetForm()
  showEditor.value = true
}

function normalizeEvents(events: unknown): string[] {
  if (Array.isArray(events)) {
    return Array.from(new Set(events.map(item => String(item).trim()).filter(Boolean)))
  }
  if (typeof events === 'string') {
    return Array.from(new Set(events.split(',').map(item => item.trim()).filter(Boolean)))
  }
  return []
}

function requireWebhookEventOptions(value: unknown): WebhookEventOption[] {
  if (!Array.isArray(value)) {
    throw new Error('Webhook 事件待确认')
  }
  const seenValues = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error('Webhook 事件待确认')
    }
    const label = typeof item.label === 'string' ? item.label.trim() : ''
    const optionValue = typeof item.value === 'string' ? item.value.trim() : ''
    if (!label || !optionValue || seenValues.has(optionValue)) {
      throw new Error('Webhook 事件待确认')
    }
    seenValues.add(optionValue)
    return { label, value: optionValue }
  })
}

function eventLabels(events: unknown) {
  return normalizeEvents(events)
    .map(value => eventLabelMap.value[value] || value)
    .join(' / ')
}

function editWebhook(w: WebhookRecord) {
  editingId.value = w.id
  form.name = w.name
  form.url = w.url
  form.events = normalizeEvents(w.events)
  form.secret = w.secret || ''
  showEditor.value = true
}

async function handleSave() {
  if (!form.name || !form.url) { message.error('名称和 URL 为必填'); return }
  if (form.events.length === 0) { message.error('请至少选择一个触发事件'); return }
  if (!/^https?:\/\//i.test(form.url.trim())) { message.error('Webhook 回调地址仅支持 http 或 https'); return }
  const eventsValue = form.events.join(',')
  try {
    const previousTotal = total.value
    const params = `name=${encodeURIComponent(form.name)}&url=${encodeURIComponent(form.url)}&events=${encodeURIComponent(eventsValue)}&secret=${encodeURIComponent(form.secret)}`
    if (editingId.value) {
      const currentEditingId = editingId.value
      const res = await request.put<unknown>(`/api/admin/webhooks/${currentEditingId}?${params}`)
      requireWebhook(getResponseData(res, 'Webhook 更新结果待确认'), 'update')
      await load()
      const refreshed = list.value?.find(item => Number(item.id) === currentEditingId)
      if (
        !refreshed
        || refreshed.name !== form.name
        || refreshed.url !== form.url
        || normalizeEvents(refreshed.events).join(',') !== eventsValue
        || (typeof previousTotal === 'number' && total.value !== previousTotal)
      ) {
        throw new Error('Webhook 更新结果待确认')
      }
      message.success('已更新')
    } else {
      const res = await request.post<unknown>(`/api/admin/webhooks?${params}`)
      const created = requireWebhook(getResponseData(res, 'Webhook 创建结果待确认'), 'create')
      page.value = 1
      await load()
      const refreshed = list.value?.find(item => Number(item.id) === created.id)
      if (
        !refreshed
        || refreshed.name !== form.name
        || refreshed.url !== form.url
        || normalizeEvents(refreshed.events).join(',') !== eventsValue
        || (typeof previousTotal === 'number' && total.value !== previousTotal + 1)
      ) {
        throw new Error('Webhook 创建结果待确认')
      }
      message.success('已创建')
    }
    showEditor.value = false
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '操作失败')
  }
}

async function toggleStatus(w: WebhookRecord) {
  const current = normalizeBinaryStatus(w.status)
  if (current === null) { message.error('Webhook 状态尚未明确，暂时无法切换'); return }
  try {
    const eventsValue = encodeURIComponent(normalizeEvents(w.events).join(','))
    const res = await request.put<unknown>(`/api/admin/webhooks/${w.id}?name=${encodeURIComponent(w.name)}&url=${encodeURIComponent(w.url)}&events=${eventsValue}&secret=${encodeURIComponent(w.secret || '')}&status=${current ? 0 : 1}`)
    requireWebhook(getResponseData(res, 'Webhook 状态待确认'), 'update')
    await load()
    const refreshed = list.value?.find(item => Number(item.id) === Number(w.id))
    if (
      !refreshed
      || normalizeBinaryStatus(refreshed.status) !== !current
      || normalizeEvents(refreshed.events).join(',') !== normalizeEvents(w.events).join(',')
      || refreshed.name !== w.name
      || refreshed.url !== w.url
      || (refreshed.secret || '') !== (w.secret || '')
    ) {
      throw new Error('Webhook 状态待确认')
    }
  } catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '操作失败')
  }
}

async function handleDelete(id: number) {
  try {
    const previousTotal = total.value
    await request.delete(`/api/admin/webhooks/${id}`)
    if ((list.value?.length || 0) === 1 && page.value > 1) {
      page.value--
    }
    await load()
    if (list.value?.some(item => Number(item.id) === id)) {
      throw new Error('Webhook 删除结果待确认')
    }
    if (typeof previousTotal === 'number' && total.value !== Math.max(0, previousTotal - 1)) {
      throw new Error('Webhook 删除结果待确认')
    }
    message.success('已删除')
  }
  catch (err: unknown) {
    message.error(err instanceof Error && err.message ? err.message : '删除失败')
  }
}

function normalizeBinaryStatus(value: unknown): boolean | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return true
  if (value === 0 || value === '0' || value === false || value === 'false') return false
  return null
}
</script>

<style scoped>
.admin-webhooks { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.count { font-size: 12px; color: #9ca3af; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #f59e0b; }
.modal-status { margin-top: -4px; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 13px; }
.empty-cell { text-align: center; padding: 24px !important; color: #9ca3af; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
