<template>
  <div class="admin-users">
    <div class="page-header">
      <h2>用户管理 (User Management)</h2>
      <p class="subtitle">管理系统用户账号、角色与状态。支持创建、编辑、重置密码、导入导出。</p>
    </div>

    <n-card class="glass-card" :bordered="false">
      <!-- 工具栏 -->
      <div class="toolbar">
        <n-input v-model:value="search" placeholder="搜索用户名或昵称..." style="width:260px;" clearable size="small" @update:value="onSearch">
          <template #prefix><Search class="s-icon" /></template>
        </n-input>
        <n-space>
          <n-button size="small" quaternary @click="loadUsers">
            <template #icon><RefreshCw class="s-icon" /></template>
          </n-button>
          <n-button size="small" secondary @click="handleExport">
            <template #icon><Download class="s-icon" /></template>导出
          </n-button>
          <n-button size="small" secondary @click="triggerImport">
            <template #icon><Upload class="s-icon" /></template>导入
          </n-button>
          <span class="count">共 {{ totalCount }} 个用户</span>
          <n-button type="primary" size="small" @click="showCreate = true">
            <template #icon><Plus /></template>创建用户
          </n-button>
        </n-space>
      </div>
      <p class="import-tip">导入 CSV 若未提供密码列，将自动使用默认密码 <code>123456</code>。</p>

      <!-- 加载态 -->
      <template v-if="loading">
        <n-skeleton text :repeat="6" style="margin:8px 0;" />
      </template>

      <!-- 表格 -->
      <template v-else>
        <n-table :single-line="false" class="admin-table">
          <thead>
            <tr>
              <th style="width:50px">ID</th>
              <th style="width:45px">头像</th>
              <th style="width:120px">用户名</th>
              <th>昵称</th>
              <th style="width:90px">角色</th>
              <th style="width:65px">状态</th>
              <th style="width:140px">创建时间</th>
              <th style="width:210px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in pagedUsers" :key="u.id">
              <td><code>#{{ u.id }}</code></td>
              <td>
                <n-avatar
                  v-if="u.avatar"
                  :src="u.avatar"
                  :size="32"
                  round
                  fallback-src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 32 32'%3E%3Crect fill='%23374151' width='32' height='32' rx='16'/%3E%3Ctext x='16' y='21' text-anchor='middle' fill='%239ca3af' font-size='14'%3E{{ u.nickname?.[0] || u.username?.[0] }}%3C/text%3E%3C/svg%3E"
                />
                <div v-else class="avatar-placeholder">{{ (u.nickname || u.username)?.[0] }}</div>
              </td>
              <td>{{ u.username }}</td>
              <td>
                <div class="editable-name">
                  <template v-if="editingId === u.id">
                    <n-input
                      v-model:value="editNickname"
                      size="tiny"
                      :maxlength="30"
                      style="width:140px;"
                      @keyup.enter="confirmEdit(u.id)"
                      @keyup.escape="cancelEdit"
                      @blur="confirmEdit(u.id)"
                    />
                  </template>
                  <template v-else>
                    <span class="name-text" @click="startEdit(u)">{{ u.nickname || '-' }}</span>
                  </template>
                </div>
              </td>
              <td>
                <n-select
                  :value="u.role"
                  :options="roleOptions"
                  size="tiny"
                  style="width:90px"
                  @update:value="(val: string) => handleUpdateRole(u.id, val)"
                />
              </td>
              <td>
                <n-switch
                  :value="u.status !== 0"
                  :disabled-update-value="true"
                  @update:value="(val: boolean) => handleToggleStatus(u, val)"
                />
              </td>
              <td style="font-size:12px;">
                <template v-if="u.createdAt">
                  <n-popover trigger="hover" placement="top">
                    <template #trigger>
                      <span>{{ formatShort(u.createdAt) }}</span>
                    </template>
                    <span>{{ formatFull(u.createdAt) }}</span>
                  </n-popover>
                </template>
                <span v-else class="never-used">-</span>
              </td>
              <td>
                <n-space :size="4">
                  <n-button size="tiny" tertiary @click="handleResetPassword(u)">重置密码</n-button>
                  <n-button size="tiny" type="error" tertiary @click="handleDeleteUser(u)">删除</n-button>
                </n-space>
              </td>
            </tr>
          </tbody>
        </n-table>

        <!-- 空状态 -->
        <div v-if="pagedUsers.length === 0 && !loading" class="empty-row">
          <n-empty description="暂无匹配的用户" />
        </div>

        <!-- 分页 -->
        <div class="pagination-wrap" v-if="totalCount > pageSize">
          <n-pagination
            v-model:page="page"
            :page-count="pageCount"
            :page-size="pageSize"
            :page-slot="7"
            size="small"
          />
        </div>
      </template>
    </n-card>

    <!-- 创建用户 -->
    <n-modal v-model:show="showCreate" preset="card" title="创建用户" style="width:480px;" closable>
      <n-form :model="createForm" label-placement="top">
        <n-form-item label="用户名" required>
          <n-input v-model:value="createForm.username" placeholder="登录用账号" :maxlength="30" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input v-model:value="createForm.password" placeholder="留空则自动生成随机初始密码" :maxlength="60" />
        </n-form-item>
        <n-form-item label="昵称">
          <n-input v-model:value="createForm.nickname" placeholder="留空则同用户名" :maxlength="30" />
        </n-form-item>
        <n-form-item label="角色">
          <n-select v-model:value="createForm.role" :options="roleOptions" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-button @click="showCreate = false">取消</n-button>
        <n-button type="primary" @click="handleCreate" :loading="creating">创建</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { Search, Download, Upload, Plus, RefreshCw } from 'lucide-vue-next'
import request from '@/api/request'

const message = useMessage()
const dialog = useDialog()

// --- 数据 ---
const users = ref<any[]>([])
const search = ref('')
const loading = ref(true)

// --- 分页 ---
const page = ref(1)
const pageSize = 10

// --- 内联编辑 ---
const editingId = ref<number | null>(null)
const editNickname = ref('')

// --- 创建 ---
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref({ username: '', password: '', nickname: '', role: 'user' })

const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' }
]

// --- 计算 ---
const filteredUsers = computed(() => {
  if (!search.value) return users.value
  const q = search.value.toLowerCase()
  return users.value.filter(u =>
    u.username?.toLowerCase().includes(q) ||
    u.nickname?.toLowerCase().includes(q)
  )
})

const totalCount = computed(() => filteredUsers.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize)))

const pagedUsers = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredUsers.value.slice(start, start + pageSize)
})

// --- 生命周期 ---
onMounted(loadUsers)

async function loadUsers() {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (search.value) params.search = search.value
    const res = await request.get('/api/admin/users', { params })
    users.value = (res as any).data || []
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadUsers()
}

// --- 创建 ---
async function handleCreate() {
  if (!createForm.value.username) { message.error('请输入用户名'); return }
  creating.value = true
  try {
    const params = new URLSearchParams({ username: createForm.value.username })
    if (createForm.value.password) params.set('password', createForm.value.password)
    if (createForm.value.nickname) params.set('nickname', createForm.value.nickname)
    params.set('role', createForm.value.role)
    const res = await request.post('/api/admin/users', params.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
    showCreate.value = false
    const initial = (res as any).data?.initialPassword
    if (initial) {
      dialog.success({
        title: '用户已创建',
        content: `初始密码：${initial}（请妥善转交用户，关闭后将无法再次查看）`,
        positiveText: '我已记录'
      })
    } else {
      message.success('用户已创建')
    }
    createForm.value = { username: '', password: '', nickname: '', role: 'user' }
    await loadUsers()
  } catch (err: any) {
    message.error(err.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// --- 角色更新 ---
async function handleUpdateRole(id: number, role: string) {
  try {
    await request.put(`/api/admin/users/${id}/role?role=${role}`)
    message.success('角色已更新')
  } catch { message.error('更新失败') }
}

// --- 状态切换 ---
async function handleToggleStatus(u: any, enabled: boolean) {
  try {
    await request.put(`/api/admin/users/${u.id}/status?status=${enabled ? 1 : 0}`)
    u.status = enabled ? 1 : 0
    message.success(enabled ? '已启用' : '已禁用')
  } catch { message.error('操作失败') }
}

// --- 内联编辑昵称 ---
function startEdit(u: any) {
  editingId.value = u.id
  editNickname.value = u.nickname || ''
  setTimeout(() => {
    const el = document.querySelector('.editable-name input') as HTMLInputElement
    el?.focus()
    el?.select()
  }, 50)
}

function cancelEdit() {
  editingId.value = null
  editNickname.value = ''
}

async function confirmEdit(id: number) {
  if (editingId.value !== id) return
  const name = editNickname.value.trim()
  editingId.value = null
  try {
    await request.put(`/api/admin/users/${id}/nickname?nickname=${encodeURIComponent(name)}`)
    message.success('昵称已更新')
    const user = users.value.find(u => u.id === id)
    if (user) user.nickname = name
  } catch (err: any) {
    message.error(err.message || '更新失败')
  }
}

// --- 重置密码 ---
async function handleResetPassword(u: any) {
  dialog.info({
    title: '重置密码',
    content: `确定要重置用户「${u.username}」的密码吗？系统将生成一个随机初始密码。`,
    positiveText: '确定重置',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.put(`/api/admin/users/${u.id}/reset-password`)
        const initial = (res as any).data?.initialPassword
        if (initial) {
          dialog.success({
            title: '密码已重置',
            content: `用户「${u.username}」的新密码：${initial}（请妥善转交，关闭后无法再次查看）`,
            positiveText: '我已记录'
          })
        } else {
          message.success(`用户「${u.username}」密码已重置`)
        }
      } catch { message.error('操作失败') }
    }
  })
}

// --- 删除 ---
async function handleDeleteUser(u: any) {
  dialog.warning({
    title: '确认删除',
    content: `确定要永久删除用户「${u.username}」（ID: ${u.id}）吗？该操作不可恢复。`,
    positiveText: '确认删除',
    negativeText: '取消',
    type: 'error',
    onPositiveClick: async () => {
      try {
        await request.delete(`/api/admin/users/${u.id}`)
        users.value = users.value.filter(x => x.id !== u.id)
        message.success('已删除')
      } catch { message.error('删除失败') }
    }
  })
}

// --- 导出 ---
async function handleExport() {
  const token = localStorage.getItem('satoken')
  try {
    const r = await fetch('http://localhost:8080/api/admin/users/export', {
      headers: { 'satoken': token || '' }
    })
    const blob = await r.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `users_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch { message.error('导出失败') }
}

// --- 导入 ---
function triggerImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.csv'
  input.onchange = async (e: any) => {
    const file = e.target?.files?.[0]
    if (!file) return
    try {
      const text = await file.text()
      const token = localStorage.getItem('satoken')
      const res = await fetch('http://localhost:8080/api/admin/users/import', {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain;charset=UTF-8', 'satoken': token || '' },
        body: text
      })
      const json = await res.json()
      if (json.code === 200) {
        message.success(`导入完成：成功 ${json.data?.success} 条，失败 ${json.data?.failed} 条`)
        await loadUsers()
      } else message.error(json.message || '导入失败')
    } catch (err: any) { message.error('导入失败: ' + (err.message || '文件格式错误')) }
  }
  input.click()
}

// --- 时间格式化 ---
function formatShort(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 16)
}

function formatFull(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.admin-users { padding-bottom: 40px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }
.glass-card { backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 8px; }
.count { font-size: 12px; color: var(--text-muted); }
.import-tip { margin: -4px 0 16px; font-size: 12px; color: var(--text-muted); }
.s-icon { width: 14px; height: 14px; color: var(--text-muted); }
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(128,128,128,0.02) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px; }
.admin-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary); padding: 8px; font-size: 13px; }
.empty-row { text-align: center; padding: 30px !important; }

.avatar-placeholder { width: 32px; height: 32px; border-radius: 50%; background: rgba(128,128,128,0.12); color: var(--text-muted); display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; }

.editable-name { min-height: 22px; }
.name-text { cursor: pointer; border-bottom: 1px dashed transparent; padding: 1px 2px; border-radius: 2px; transition: all .15s; }
.name-text:hover { border-bottom-color: var(--text-muted); background: rgba(128,128,128,0.06); }

.never-used { color: var(--text-muted); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>