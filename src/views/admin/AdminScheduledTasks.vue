<template>
  <div class="admin-scheduler">
    <div class="page-header">
      <h2>定时任务调度 (Scheduled Tasks)</h2>
      <p class="subtitle">自定义管理系统定时任务，如自动清理、统计快照等。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <div class="toolbar">
        <span class="count">共 {{ tasks.length }} 个任务</span>
        <n-button type="primary" size="small" @click="openCreate">
          <template #icon><Plus /></template>新建任务
        </n-button>
      </div>

      <n-table :single-line="false" class="admin-table">
        <thead>
          <tr>
            <th style="width:50px">ID</th>
            <th>任务名称</th>
            <th>描述</th>
            <th style="width:110px">类型</th>
            <th style="width:110px">Cron</th>
            <th style="width:60px">启用</th>
            <th style="width:150px">上次运行</th>
            <th style="width:70px">结果</th>
            <th style="width:180px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tasks" :key="t.id">
            <td><code>#{{ t.id }}</code></td>
            <td>{{ t.name }}</td>
            <td><n-ellipsis :line-clamp="1">{{ t.description }}</n-ellipsis></td>
            <td><n-tag size="small">{{ t.taskType }}</n-tag></td>
            <td><n-tag size="small" type="info">{{ t.cron }}</n-tag></td>
            <td><n-switch :value="t.enabled === 1" @update:value="handleToggle(t.id)" /></td>
            <td style="font-size:12px;">{{ t.lastRunAt ? t.lastRunAt.substring(0,19).replace('T',' ') : '-' }}</td>
            <td><n-tag size="small" :type="t.lastStatus === 'success' ? 'success' : t.lastStatus === 'failed' ? 'error' : 'default'">{{ t.lastStatus || '-' }}</n-tag></td>
            <td>
              <n-space>
                <n-button size="tiny" secondary @click="handleRunNow(t.id)" :loading="runningId === t.id" title="立即执行">
                  <template #icon><Play /></template>
                </n-button>
                <n-button size="tiny" secondary @click="openEdit(t)" title="编辑">
                  <template #icon><Edit3 /></template>
                </n-button>
                <n-button size="tiny" type="error" tertiary @click="handleDelete(t.id)" title="删除">
                  <template #icon><Trash2 /></template>
                </n-button>
              </n-space>
            </td>
          </tr>
        </tbody>
      </n-table>
    </n-card>

    <!-- 新建/编辑弹窗 -->
    <n-modal v-model:show="showEditor" preset="card" :title="editingId ? '编辑定时任务' : '新建定时任务'" style="width:560px;" closable>
      <n-form :model="form" label-placement="top">
        <n-form-item label="任务名称" required>
          <n-input v-model:value="form.name" placeholder="例如：每日凌晨清理旧数据" :maxlength="60" show-count />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="form.description" placeholder="描述这个任务的作用" />
        </n-form-item>
        <n-row :gutter="12">
          <n-col :span="12">
            <n-form-item label="任务类型" required>
              <n-select v-model:value="form.taskType" :options="typeOptions" />
            </n-form-item>
          </n-col>
          <n-col :span="12">
            <n-form-item label="Cron 表达式" required>
              <n-input v-model:value="form.cron" placeholder="如 0 0 3 * * ? (每天3点)" />
            </n-form-item>
          </n-col>
        </n-row>
        <div class="cron-hint">
          <span>常用示例：</span>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 3 * * ?'">每天3点</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 * * * ?'">每小时</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 30 6 * * ?'">每天6:30</n-button>
          <n-button size="tiny" quaternary @click="form.cron = '0 0 12 * * ?'">每天12点</n-button>
        </div>
        <n-form-item v-if="form.taskType === 'cleanup'" label="保留天数">
          <n-input-number v-model:value="cleanupDays" :min="1" :max="365" style="width:100%;" />
          <div class="cron-hint" style="margin-top:4px;">超过此天数的数据将被清理</div>
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditor = false">取消</n-button>
          <n-button type="primary" @click="handleSave">{{ editingId ? '保存修改' : '创建任务' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { Plus, Play, Edit3, Trash2 } from 'lucide-vue-next'
import request, { clearCache } from '@/api/request'

const message = useMessage()
const tasks = ref<any[]>([])
const runningId = ref<number | null>(null)
const showEditor = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  name: '', description: '', taskType: 'cleanup', cron: '0 0 3 * * ?'
})
const cleanupDays = ref(30)

const typeOptions = [
  { label: '数据清理', value: 'cleanup' },
  { label: '统计快照', value: 'stats' }
]

onMounted(load)

async function load() {
  clearCache('scheduled-tasks')
  try { const res = await request.get('/api/admin/scheduled-tasks', { headers: { 'x-no-cache': '1' } }); tasks.value = (res as any).data || [] } catch {}
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.description = ''
  form.taskType = 'cleanup'
  form.cron = '0 0 3 * * ?'
  cleanupDays.value = 30
  showEditor.value = true
}

function openEdit(task: any) {
  editingId.value = task.id
  form.name = task.name
  form.description = task.description || ''
  form.taskType = task.taskType
  form.cron = task.cron
  cleanupDays.value = 30
  if (task.configJson) {
    try { cleanupDays.value = JSON.parse(task.configJson).daysOld || 30 } catch {}
  }
  showEditor.value = true
}

async function handleSave() {
  if (!form.name || !form.cron) { message.error('名称和 Cron 表达式为必填'); return }
  const payload: any = {
    name: form.name,
    description: form.description,
    taskType: form.taskType,
    cron: form.cron
  }
  if (form.taskType === 'cleanup') {
    payload.configJson = JSON.stringify({ daysOld: cleanupDays.value })
  }
  try {
    if (editingId.value) {
      await request.put(`/api/admin/scheduled-tasks/${editingId.value}`, payload)
      message.success('任务已更新')
    } else {
      await request.post('/api/admin/scheduled-tasks', payload)
      message.success('任务已创建')
    }
    showEditor.value = false
    await load()
  } catch (err: any) { message.error(err.message || '操作失败') }
}

async function handleDelete(id: number) {
  try { await request.delete(`/api/admin/scheduled-tasks/${id}`); tasks.value = tasks.value.filter(t => t.id !== id); message.success('已删除') }
  catch (err: any) { message.error(err.message || '删除失败') }
}

async function handleToggle(id: number) {
  try { await request.post(`/api/admin/scheduled-tasks/${id}/toggle`); await load() } catch { message.error('操作失败') }
}

async function handleRunNow(id: number) {
  runningId.value = id
  try {
    await request.post(`/api/admin/scheduled-tasks/${id}/run`)
    message.success('任务已触发')
    await load()
  } catch { message.error('执行失败') }
  finally { runningId.value = null }
}
</script>

<style scoped>
.admin-scheduler { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { background: rgba(15,23,42,0.4) !important; backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.count { font-size: 12px; color: var(--text-muted); }
.cron-hint { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 12px; color: var(--text-muted); margin-bottom: 10px; }
</style>
