<template>
  <div class="admin-webhooks">
    <div class="page-header">
      <h2>Webhook 管理 (Webhooks)</h2>
      <p class="subtitle">配置 Webhook，任务完成或失败时自动通知外部系统。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <span class="count">共 {{ list.length }} 个 Webhook</span>
        <n-button type="primary" @click="showEditor = true; editingId = null; resetForm()">
          <template #icon><Plus /></template>新建
        </n-button>
      </div>

      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr><th style="width:60px">ID</th><th>名称</th><th>URL</th><th style="width:140px">事件</th><th style="width:80px">状态</th><th style="width:140px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="w in list" :key="w.id">
            <td><code>#{{ w.id }}</code></td>
            <td>{{ w.name }}</td>
            <td><n-ellipsis :line-clamp="1" :tooltip="true" style="max-width:300px;">{{ w.url }}</n-ellipsis></td>
            <td><n-tag size="small">{{ eventLabels(w.events) }}</n-tag></td>
            <td><n-switch :value="w.status === 1" @update:value="toggleStatus(w)" /></td>
            <td>
              <n-space>
                <n-button size="tiny" secondary @click="editWebhook(w)">编辑</n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(w.id)">删除</n-button>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
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
          <n-select v-model:value="form.events" :options="eventOptions" multiple />
        </n-form-item>
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

const message = useMessage()
const list = ref<any[]>([])
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const eventOptions = ref<{ label: string; value: string }[]>([])
const form = reactive({ name: '', url: '', events: ['task.completed'] as string[], secret: '' })
const eventLabelMap = computed(() => Object.fromEntries(eventOptions.value.map(item => [item.value, item.label])))

onMounted(async () => {
  await loadEventOptions()
  await load()
})

async function load() {
  try { const res = await request.get('/api/admin/webhooks'); list.value = (res as any).data || [] }
  catch (err: any) { message.error(err.message || '加载 Webhook 列表失败') }
}

async function loadEventOptions() {
  try {
    const res = await request.get('/api/admin/webhooks/meta')
    const options = Array.isArray((res as any).data) ? (res as any).data : []
    eventOptions.value = options
    if (options.length > 0 && form.events.length === 0) {
      form.events = [options[0].value]
    }
  } catch {
    eventOptions.value = [
      { label: '任务开始', value: 'task.started' },
      { label: '任务完成', value: 'task.completed' },
      { label: '任务失败', value: 'task.failed' }
    ]
  }
}

function resetForm() { Object.assign(form, { name: '', url: '', events: ['task.completed'], secret: '' }) }

function normalizeEvents(events: unknown): string[] {
  if (Array.isArray(events)) {
    return events.map(item => String(item).trim()).filter(Boolean)
  }
  if (typeof events === 'string') {
    return events.split(',').map(item => item.trim()).filter(Boolean)
  }
  return []
}

function eventLabels(events: unknown) {
  return normalizeEvents(events)
    .map(value => eventLabelMap.value[value] || value)
    .join(' / ')
}

function editWebhook(w: any) {
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
  const eventsValue = form.events.join(',')
  try {
    const params = `name=${encodeURIComponent(form.name)}&url=${encodeURIComponent(form.url)}&events=${encodeURIComponent(eventsValue)}&secret=${encodeURIComponent(form.secret)}`
    if (editingId.value) {
      await request.put(`/api/admin/webhooks/${editingId.value}?${params}`)
      message.success('已更新')
    } else {
      await request.post(`/api/admin/webhooks?${params}`)
      message.success('已创建')
    }
    showEditor.value = false; await load()
  } catch { message.error('操作失败') }
}

async function toggleStatus(w: any) {
  try {
    await request.put(`/api/admin/webhooks/${w.id}?name=${encodeURIComponent(w.name)}&url=${encodeURIComponent(w.url)}&events=${encodeURIComponent(Array.isArray(w.events) ? w.events.join(',') : w.events)}&status=${w.status === 1 ? 0 : 1}`)
    w.status = w.status === 1 ? 0 : 1
  } catch { message.error('操作失败') }
}

async function handleDelete(id: number) {
  try { await request.delete(`/api/admin/webhooks/${id}`); list.value = list.value.filter(w => w.id !== id); message.success('已删除') }
  catch { message.error('删除失败') }
}
</script>

<style scoped>
.admin-webhooks { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.count { font-size: 12px; color: #9ca3af; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 13px; }
</style>
