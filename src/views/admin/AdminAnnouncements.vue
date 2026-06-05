<template>
  <div class="admin-announcements">
    <div class="page-header">
      <h2>系统公告 (Announcements)</h2>
      <p class="subtitle">发布和管理系统公告，所有用户可在控制台查看。</p>
    </div>

    <!-- 新建按钮 -->
    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <span class="count">共 {{ listCountDisplay }} 条公告</span>
        <n-button type="primary" @click="showEditor = true; editingId = null; Object.assign(form, { title: '', content: '', priority: 'normal' })">
          <template #icon><Plus /></template>新建公告
        </n-button>
      </div>
      <div v-if="listLoadState === 'error'" class="status-note">公告数据待确认，请稍后重试。</div>

      <div v-if="loadingList && list === null" class="loading-box">
        <n-spin size="small" />
      </div>
      <n-table v-else :single-line="false" class="admin-table">
        <thead>
          <tr><th style="width:60px">ID</th><th>标题</th><th style="width:80px">优先级</th><th style="width:80px">状态</th><th style="width:200px">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in list || []" :key="a.id">
            <td><code>#{{ a.id }}</code></td>
            <td>{{ a.title }}</td>
            <td><n-tag size="small" :type="a.priority === 'high' ? 'error' : a.priority === 'normal' ? 'info' : 'default'">{{ a.priority }}</n-tag></td>
            <td>
              <n-tag v-if="normalizeBinaryStatus(a.status) === null" size="small" type="warning">状态待确认</n-tag>
              <n-switch v-else :value="normalizeBinaryStatus(a.status) === true" @update:value="handleToggle(a.id)" />
            </td>
            <td>
              <n-space>
                <n-button size="tiny" secondary @click="editAnnouncement(a)">编辑</n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(a.id)">删除</n-button>
              </n-space>
            </td>
          </tr>
          <tr v-if="list !== null && list.length === 0">
            <td colspan="5" class="empty-cell">暂无公告</td>
          </tr>
          <tr v-else-if="list === null">
            <td colspan="5" class="empty-cell">公告数据待确认，请稍后重试。</td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 编辑弹窗 -->
    <n-modal v-model:show="showEditor" preset="card" :title="editingId ? '编辑公告' : '新建公告'" style="width:520px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="标题">
          <n-input v-model:value="form.title" placeholder="公告标题..." />
        </n-form-item>
        <n-form-item label="内容">
          <n-input v-model:value="form.content" type="textarea" :autosize="{ minRows: 4, maxRows: 10 }" placeholder="公告正文..." />
        </n-form-item>
        <n-form-item label="优先级">
          <n-select v-model:value="form.priority" :options="[{label:'普通',value:'normal'},{label:'高',value:'high'},{label:'低',value:'low'}]" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button type="primary" @click="handleSave">{{ editingId ? '更新' : '发布' }}</n-button>
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
const loadingList = ref(true)
const list = ref<any[] | null>(null)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ title: '', content: '', priority: 'normal' })
const listLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const listCountDisplay = computed(() => list.value === null ? '-' : list.value.length)

function requireAnnouncement(value: unknown, action: 'create' | 'update') {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(action === 'create' ? '公告发布结果待确认' : '公告更新结果待确认')
  }
  const id = Number((value as any).id)
  const title = typeof (value as any).title === 'string' ? (value as any).title.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !title) {
    throw new Error(action === 'create' ? '公告发布结果待确认' : '公告更新结果待确认')
  }
  return {
    id,
    title,
    content: typeof (value as any).content === 'string' ? (value as any).content.trim() : '',
    priority: typeof (value as any).priority === 'string' ? (value as any).priority.trim() : '',
    status: normalizeBinaryStatus((value as any).status)
  }
}

function normalizeAnnouncementRecord(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('公告数据待确认')
  }
  const announcement = requireAnnouncement(value, 'update')
  if (!announcement.content || !announcement.priority) {
    throw new Error('公告数据待确认')
  }
  return {
    ...(value as Record<string, unknown>),
    id: announcement.id,
    title: announcement.title,
    content: announcement.content,
    priority: announcement.priority,
    status: announcement.status
  }
}

onMounted(load)

async function load() {
  loadingList.value = true
  listLoadState.value = 'loading'
  try {
    const res = await request.get('/api/admin/announcements')
    const data = (res as any).data
    if (!Array.isArray(data)) {
      throw new Error('公告数据待确认')
    }
    list.value = data.map((item: unknown) => normalizeAnnouncementRecord(item))
    listLoadState.value = 'ready'
  } catch (err: any) {
    list.value = null
    listLoadState.value = 'error'
    message.error(err.message || '加载公告失败')
  } finally {
    loadingList.value = false
  }
}

function editAnnouncement(a: any) {
  editingId.value = a.id; form.title = a.title; form.content = a.content; form.priority = a.priority; showEditor.value = true
}

async function handleSave() {
  if (!form.title || !form.content) { message.error('标题和内容不能为空'); return }
  try {
    const previousCount = list.value?.length
    if (editingId.value) {
      const currentEditingId = editingId.value
      const res = await request.put(`/api/admin/announcements/${currentEditingId}?title=${encodeURIComponent(form.title)}&content=${encodeURIComponent(form.content)}&priority=${form.priority}`)
      requireAnnouncement((res as any).data, 'update')
      await load()
      const refreshed = list.value?.find(item => Number(item.id) === currentEditingId)
      if (!refreshed || refreshed.title !== form.title || refreshed.content !== form.content || refreshed.priority !== form.priority) {
        throw new Error('公告更新结果待确认')
      }
      if (typeof previousCount === 'number' && list.value?.length !== previousCount) {
        throw new Error('公告更新结果待确认')
      }
      message.success('已更新')
    } else {
      const res = await request.post(`/api/admin/announcements?title=${encodeURIComponent(form.title)}&content=${encodeURIComponent(form.content)}&priority=${form.priority}`)
      const created = requireAnnouncement((res as any).data, 'create')
      await load()
      const refreshed = list.value?.find(item => Number(item.id) === created.id)
      if (!refreshed || refreshed.title !== form.title || refreshed.content !== form.content || refreshed.priority !== form.priority) {
        throw new Error('公告发布结果待确认')
      }
      if (typeof previousCount === 'number' && typeof list.value?.length === 'number' && list.value.length < previousCount + 1) {
        throw new Error('公告发布结果待确认')
      }
      message.success('已发布')
    }
    showEditor.value = false
  } catch (err: any) { message.error(err.message || '操作失败') }
}

async function handleToggle(id: number) {
  const currentAnnouncement = list.value?.find(item => Number(item.id) === id)
  const currentStatus = normalizeBinaryStatus(currentAnnouncement?.status)
  if (currentStatus === null) {
    message.error('公告状态尚未明确，暂时无法切换')
    return
  }
  try {
    await request.post(`/api/admin/announcements/${id}/toggle`)
    await load()
    const refreshed = list.value?.find(item => Number(item.id) === id)
    if (!refreshed || normalizeBinaryStatus(refreshed.status) !== !currentStatus) {
      throw new Error('公告状态待确认')
    }
  } catch (err: any) { message.error(err.message || '操作失败') }
}

async function handleDelete(id: number) {
  try {
    const previousCount = list.value?.length
    await request.delete(`/api/admin/announcements/${id}`)
    await load()
    if (list.value?.some(item => Number(item.id) === id)) {
      throw new Error('公告删除结果待确认')
    }
    if (typeof previousCount === 'number' && typeof list.value?.length === 'number' && list.value.length > Math.max(0, previousCount - 1)) {
      throw new Error('公告删除结果待确认')
    }
    message.success('已删除')
  }
  catch (err: any) { message.error(err.message || '删除失败') }
}

function normalizeBinaryStatus(value: unknown): boolean | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return true
  if (value === 0 || value === '0' || value === false || value === 'false') return false
  return null
}
</script>

<style scoped>
.admin-announcements { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: #fff; }
.subtitle { font-size: 13px; color: #9ca3af; margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid rgba(255,255,255,0.08) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.loading-box { display: flex; justify-content: center; padding: 24px 0; }
.count { font-size: 12px; color: #9ca3af; }
.status-note { margin-bottom: 12px; font-size: 12px; color: #fca5a5; }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(255,255,255,0.02) !important; color: #9ca3af !important; border-bottom: 1px solid rgba(255,255,255,0.06) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid rgba(255,255,255,0.04) !important; color: #e5e7eb; padding: 8px; font-size: 13px; }
.empty-cell { text-align: center; padding: 24px !important; color: #9ca3af; }
</style>
