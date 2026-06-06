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
          <span class="count">共 {{ totalDisplay }} 个用户</span>
          <n-button type="primary" size="small" @click="openCreateDialog">
            <template #icon><Plus /></template>创建用户
          </n-button>
        </n-space>
      </div>
      <div v-if="roleOptionsLoadState === 'error'" class="status-note">角色选项待确认，请稍后重试。</div>
      <p class="import-tip">导入 CSV 若未提供密码列，系统会为对应账号自动生成随机初始密码，并在导入完成后一次性展示。</p>

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
            <tr v-for="u in users || []" :key="u.id">
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
                <template v-if="normalizeUserStatus(u.status) === null">
                  <n-tag size="small" type="warning">状态待确认</n-tag>
                </template>
                <n-switch
                  v-else
                  :value="normalizeUserStatus(u.status) === 1"
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
        <div v-if="users !== null && users.length === 0 && !loading" class="empty-row">
          <n-empty description="暂无匹配的用户" />
        </div>
        <div v-else-if="users === null && !loading" class="empty-row">
          <n-empty description="用户数据待确认，请稍后重试。" />
        </div>

        <!-- 分页 -->
        <div class="pagination-wrap" v-if="(total ?? 0) > pageSize">
          <n-pagination
            v-model:page="page"
            :item-count="total"
            :page-size="pageSize"
            :page-slot="7"
            size="small"
            @update:page="loadUsers"
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
          <n-input v-model:value="createForm.password" :placeholder="`留空则自动生成随机初始密码；手动设置时${PASSWORD_REQUIREMENT_TEXT}`" :maxlength="60" />
        </n-form-item>
        <n-form-item label="昵称">
          <n-input v-model:value="createForm.nickname" placeholder="留空则同用户名" :maxlength="30" />
        </n-form-item>
        <n-form-item label="角色">
          <n-select v-model:value="createForm.role" :options="roleOptions" :disabled="roleOptionsLoadState === 'error'" />
        </n-form-item>
        <div v-if="roleOptionsLoadState === 'error'" class="status-note modal-status">当前无法确认可用角色，请稍后重试。</div>
      </n-form>
      <template #footer>
        <n-button @click="showCreate = false">取消</n-button>
        <n-button type="primary" @click="handleCreate" :loading="creating">创建</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { Search, Download, Upload, Plus, RefreshCw } from 'lucide-vue-next'
import request, { API_BASE_URL } from '@/api/request'
import { PASSWORD_REQUIREMENT_TEXT, validatePasswordStrength } from '@/utils/password'
import { encryptPassword } from '@/utils/passwordEncryption'

const message = useMessage()
const dialog = useDialog()
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

// --- 数据 ---
type AdminUserRecord = {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
  status: number
  createdAt: string
}

type RoleOption = {
  label: string
  value: string
}

type CreatedUserResult = {
  id: number
  username: string
  initialPassword: string
}

type GeneratedCredential = {
  username: string
  initialPassword: string
}

type UserImportResult = {
  success: number
  failed: number
  errors: string[]
  generatedCredentials: GeneratedCredential[]
}

const users = ref<AdminUserRecord[] | null>(null)
const search = ref('')
const loading = ref(true)

// --- 分页(服务端) ---
const page = ref(1)
const pageSize = 10
const total = ref<number | null>(null)
const roleOptions = ref<RoleOption[]>([])
const roleOptionsLoadState = ref<'loading' | 'ready' | 'error'>('loading')
const totalDisplay = computed(() => total.value == null ? '-' : total.value)

// --- 内联编辑 ---
const editingId = ref<number | null>(null)
const editNickname = ref('')

// --- 创建 ---
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref({ username: '', password: '', nickname: '', role: '' })

// --- 生命周期 ---
onMounted(async () => {
  await loadRoleOptions()
  await loadUsers()
})

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

function normalizeOptionalText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function requireUsersPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('用户数据待确认')
  }
  const records = value.records
  const count = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(count) || count < 0) {
    throw new Error('用户数据待确认')
  }
  const ids = new Set<number>()
  const normalizedRecords = records.map((item: unknown) => {
    const normalized = normalizeUserRecord(item)
    if (ids.has(normalized.id)) {
      throw new Error('用户数据待确认')
    }
    ids.add(normalized.id)
    return normalized
  })
  if (normalizedRecords.length > count) {
    throw new Error('用户数据待确认')
  }
  return {
    records: normalizedRecords,
    total: count
  }
}

function normalizeUserRecord(value: unknown): AdminUserRecord {
  if (!isPlainObject(value)) {
    throw new Error('用户数据待确认')
  }
  const id = Number(value.id)
  const username = normalizeOptionalText(value.username)
  const role = normalizeOptionalText(value.role)
  const status = normalizeUserStatus(value.status)
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!Number.isFinite(id) || id <= 0 || !username || !role || status == null || !createdAt) {
    throw new Error('用户数据待确认')
  }
  return {
    id,
    username,
    nickname: normalizeOptionalText(value.nickname),
    avatar: normalizeOptionalText(value.avatar),
    role,
    status,
    createdAt
  }
}

function requireCreatedUserResult(value: unknown): CreatedUserResult {
  if (!isPlainObject(value)) {
    throw new Error('用户创建结果待确认')
  }
  const id = Number(value.id)
  const username = normalizeOptionalText(value.username)
  const role = normalizeOptionalText(value.role)
  if (!Number.isFinite(id) || id <= 0 || !username || !role) {
    throw new Error('用户创建结果待确认')
  }
  return {
    id,
    username,
    initialPassword: typeof value.initialPassword === 'string' ? value.initialPassword : ''
  }
}

function requireResetPasswordResult(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('重置后的初始密码待确认')
  }
  const initialPassword = normalizeOptionalText(value.initialPassword)
  if (!initialPassword) {
    throw new Error('重置后的初始密码待确认')
  }
  return { initialPassword }
}

function requireGeneratedCredentials(value: unknown): GeneratedCredential[] {
  if (value == null) {
    return []
  }
  if (!Array.isArray(value)) {
    throw new Error('导入结果待确认')
  }
  const usernames = new Set<string>()
  return value.map((item: unknown) => {
    if (!isPlainObject(item)) {
      throw new Error('导入结果待确认')
    }
    const username = normalizeOptionalText(item.username)
    const initialPassword = normalizeOptionalText(item.initialPassword)
    if (!username || !initialPassword || usernames.has(username)) {
      throw new Error('导入结果待确认')
    }
    usernames.add(username)
    return { username, initialPassword }
  })
}

function requireImportResult(value: unknown): UserImportResult {
  if (!isPlainObject(value)) {
    throw new Error('导入结果待确认')
  }
  const success = Number(value.success)
  const failed = Number(value.failed)
  if (!Number.isInteger(success) || success < 0 || !Number.isInteger(failed) || failed < 0) {
    throw new Error('导入结果待确认')
  }
  const errors = Array.isArray(value.errors)
    ? value.errors.map((item) => normalizeOptionalText(item)).filter(Boolean)
    : []
  return {
    success,
    failed,
    errors,
    generatedCredentials: requireGeneratedCredentials(value.generatedCredentials)
  }
}

function requireUserExportCsv(value: string) {
  const normalized = value.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n')
  if (!normalized.includes('# canary,')) {
    throw new Error('导出结果待确认')
  }
  if (!normalized.includes('ID,用户名,昵称,角色,状态')) {
    throw new Error('导出结果待确认')
  }
  const rows = normalized.split('\n').map(line => line.trim()).filter(Boolean)
  const headerIndex = rows.findIndex(line => line === 'ID,用户名,昵称,角色,状态')
  if (headerIndex < 0) {
    throw new Error('导出结果待确认')
  }
  const dataRows = rows.slice(headerIndex + 1)
  if (dataRows.some(line => line.split(',').length < 5)) {
    throw new Error('导出结果待确认')
  }
  return normalized
}

async function loadRoleOptions() {
  roleOptionsLoadState.value = 'loading'
  try {
    const response = await request.get<unknown>('/api/admin/users/roles')
    const options = requireRoleOptions(getResponseData(response, '角色选项待确认'))
    roleOptions.value = options
    roleOptionsLoadState.value = 'ready'
    if (!roleOptions.value.some(item => item.value === createForm.value.role)) {
      createForm.value.role = defaultCreateRole()
    }
  } catch {
    roleOptions.value = []
    roleOptionsLoadState.value = 'error'
    createForm.value.role = defaultCreateRole()
  }
}

function requireRoleOptions(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('角色选项待确认')
  }
  const seenValues = new Set<string>()
  return value.map((item: unknown): RoleOption => {
    if (!isPlainObject(item)) {
      throw new Error('角色选项待确认')
    }
    const label = normalizeOptionalText(item.label)
    const optionValue = normalizeOptionalText(item.value)
    if (!label || !optionValue || seenValues.has(optionValue)) {
      throw new Error('角色选项待确认')
    }
    seenValues.add(optionValue)
    return { label, value: optionValue }
  })
}

function defaultCreateRole() {
  return roleOptions.value.find(item => item.value === 'user')?.value || roleOptions.value[0]?.value || ''
}

function resetCreateForm() {
  createForm.value = { username: '', password: '', nickname: '', role: defaultCreateRole() }
}

function openCreateDialog() {
  resetCreateForm()
  showCreate.value = true
}

async function loadUsers() {
  loading.value = true
  try {
    const params: Record<string, number | string> = { page: page.value, pageSize }
    if (search.value) params.search = search.value
    const response = await request.get<unknown>('/api/admin/users', { params, headers: NO_CACHE_HEADERS })
    const data = requireUsersPage(getResponseData(response, '用户数据待确认'))
    users.value = data.records
    total.value = data.total
  } catch (err: unknown) {
    users.value = null
    total.value = null
    message.error(getErrorMessage(err, '加载用户失败'))
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
  if (createForm.value.password) {
    const passwordError = validatePasswordStrength(createForm.value.password, createForm.value.username)
    if (passwordError) { message.error(passwordError); return }
  }
  creating.value = true
  try {
    const searchKeyword = search.value.trim().toLowerCase()
    const expectedUsername = createForm.value.username.trim()
    const expectedNickname = (createForm.value.nickname || '').trim() || expectedUsername
    const expectedRole = createForm.value.role
    const params = new URLSearchParams({ username: createForm.value.username })
    if (createForm.value.password) {
      params.set('encryptedPassword', await encryptPassword(createForm.value.password))
    }
    if (createForm.value.nickname) params.set('nickname', createForm.value.nickname)
    params.set('role', createForm.value.role)
    const response = await request.post<unknown>('/api/admin/users', params.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
    const createdUser = requireCreatedUserResult(getResponseData(response, '用户创建结果待确认'))
    await loadUsers()
    const shouldAppearInCurrentList = !searchKeyword
      || createdUser.username.toLowerCase().includes(searchKeyword)
      || expectedNickname.toLowerCase().includes(searchKeyword)
    const listedUser = users.value?.find(user => Number(user.id) === createdUser.id) || null
    if (page.value === 1 && shouldAppearInCurrentList && !listedUser) {
      throw new Error('用户创建结果待确认')
    }
    const confirmedUser = listedUser || await loadUserByUsername(createdUser.username)
    if (!confirmedUser || confirmedUser.username !== expectedUsername || (confirmedUser.nickname || '') !== expectedNickname || confirmedUser.role !== expectedRole) {
      throw new Error('用户创建结果待确认')
    }
    showCreate.value = false
    const initial = createdUser.initialPassword
    if (!createForm.value.password) {
      if (typeof initial !== 'string' || !initial) {
        throw new Error('初始密码待确认')
      }
      dialog.success({
        title: '用户已创建',
        content: `初始密码：${initial}（请妥善转交用户，关闭后将无法再次查看）`,
        positiveText: '我已记录'
      })
    } else {
      message.success('用户已创建')
    }
    resetCreateForm()
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '创建失败'))
  } finally {
    creating.value = false
  }
}

// --- 角色更新 ---
async function handleUpdateRole(id: number, role: string) {
  try {
    await request.put(`/api/admin/users/${id}/role?role=${role}`)
    await loadUsers()
    const updatedUser = users.value?.find(user => Number(user.id) === id)
    if (!updatedUser || updatedUser.role !== role) {
      throw new Error('角色更新结果待确认')
    }
    message.success('角色已更新')
  } catch (err: unknown) { message.error(getErrorMessage(err, '更新失败')) }
}

// --- 状态切换 ---
async function handleToggleStatus(u: AdminUserRecord, enabled: boolean) {
  try {
    await request.put(`/api/admin/users/${u.id}/status?status=${enabled ? 1 : 0}`)
    await loadUsers()
    const updatedUser = users.value?.find(user => Number(user.id) === Number(u.id))
    if (!updatedUser || normalizeUserStatus(updatedUser.status) !== (enabled ? 1 : 0)) {
      throw new Error('用户状态待确认')
    }
    message.success(enabled ? '已启用' : '已禁用')
  } catch (err: unknown) { message.error(getErrorMessage(err, '操作失败')) }
}

function normalizeUserStatus(value: unknown): number | null {
  if (value === 1 || value === '1' || value === true || value === 'true') return 1
  if (value === 0 || value === '0' || value === false || value === 'false') return 0
  return null
}

// --- 内联编辑昵称 ---
function startEdit(u: AdminUserRecord) {
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
  const currentUser = users.value?.find(u => Number(u.id) === id) || null
  const expectedUsername = typeof currentUser?.username === 'string' ? currentUser.username : ''
  editingId.value = null
  try {
    await request.put(`/api/admin/users/${id}/nickname?nickname=${encodeURIComponent(name)}`)
    await loadUsers()
    const refreshedUser = users.value?.find(u => Number(u.id) === id) || (expectedUsername ? await loadUserByUsername(expectedUsername) : null)
    if (!refreshedUser || (refreshedUser.nickname || '') !== name) {
      throw new Error('昵称更新结果待确认')
    }
    message.success('昵称已更新')
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '更新失败'))
  }
}

// --- 重置密码 ---
async function handleResetPassword(u: AdminUserRecord) {
  dialog.info({
    title: '重置密码',
    content: `确定要重置用户「${u.username}」的密码吗？系统将生成一个随机初始密码。`,
    positiveText: '确定重置',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const response = await request.put<unknown>(`/api/admin/users/${u.id}/reset-password`)
        const result = requireResetPasswordResult(getResponseData(response, '重置后的初始密码待确认'))
        const confirmedUser = await loadUserByUsername(u.username)
        if (!confirmedUser || Number(confirmedUser.id) !== Number(u.id) || confirmedUser.username !== u.username) {
          throw new Error('用户重置密码结果待确认')
        }
        dialog.success({
          title: '密码已重置',
          content: `用户「${u.username}」的新密码：${result.initialPassword}（请妥善转交，关闭后无法再次查看）`,
          positiveText: '我已记录'
        })
      } catch (err: unknown) { message.error(getErrorMessage(err, '操作失败')) }
    }
  })
}

// --- 删除 ---
async function handleDeleteUser(u: AdminUserRecord) {
  dialog.warning({
    title: '确认删除',
    content: `确定要永久删除用户「${u.username}」（ID: ${u.id}）吗？该操作不可恢复。`,
    positiveText: '确认删除',
    negativeText: '取消',
    type: 'error',
    onPositiveClick: async () => {
      try {
        await request.delete(`/api/admin/users/${u.id}`)
        // 删除当前页最后一条时回退一页,再重新请求保持 total 同步
        if ((users.value?.length || 0) === 1 && page.value > 1) page.value--
        await loadUsers()
        if (users.value?.some(user => Number(user.id) === Number(u.id))) {
          throw new Error('删除结果待确认')
        }
        message.success('已删除')
      } catch (err: unknown) { message.error(getErrorMessage(err, '删除失败')) }
    }
  })
}

// --- 导出 ---
async function handleExport() {
  const token = localStorage.getItem('satoken')
  try {
    const r = await fetch(`${API_BASE_URL}/api/admin/users/export`, {
      headers: { 'satoken': token || '' }
    })
    if (!r.ok) {
      throw new Error(`导出接口返回 ${r.status}`)
    }
    const csvText = requireUserExportCsv(await r.text())
    const blob = new Blob([csvText], { type: 'text/csv;charset=UTF-8' })
    if (blob.size <= 0) {
      throw new Error('导出结果待确认')
    }
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `users_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch (err: unknown) { message.error(getErrorMessage(err, '导出失败')) }
}

// --- 导入 ---
function triggerImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.csv'
  input.onchange = async (event: Event) => {
    const target = event.target
    const file = target instanceof HTMLInputElement ? target.files?.[0] : undefined
    if (!file) return
    try {
      const previousTotal = total.value
      const previousSearch = search.value.trim()
      const text = await file.text()
      const response = await request.post<unknown>('/api/admin/users/import', text, {
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' }
      })
      const importResult = requireImportResult(getResponseData(response, '导入结果待确认'))
      const generatedCredentials = importResult.generatedCredentials
      await loadUsers()
      if (importResult.success > 0 && total.value === null) {
        throw new Error('导入结果待确认')
      }
      if (importResult.success > 0 && !previousSearch && previousTotal !== null && total.value !== null && total.value < previousTotal + importResult.success) {
        throw new Error('导入结果待确认')
      }
      for (const item of generatedCredentials.slice(0, 5)) {
        const confirmedUser = await loadUserByUsername(item.username)
        if (!confirmedUser || confirmedUser.username !== item.username) {
          throw new Error('导入结果待确认')
        }
      }
      message.success(`导入完成：成功 ${importResult.success} 条，失败 ${importResult.failed} 条`)
      if (generatedCredentials.length > 0) {
        dialog.success({
          title: '导入成功',
          content: buildGeneratedCredentialSummary(generatedCredentials),
          positiveText: '我已记录'
        })
      }
    } catch (err: unknown) { message.error(`导入失败: ${getErrorMessage(err, '文件格式错误')}`) }
  }
  input.click()
}

async function loadUserByUsername(username: string) {
  const keyword = username.trim()
  if (!keyword) return null
  const response = await request.get<unknown>('/api/admin/users', {
    params: {
      page: 1,
      pageSize: 100,
      search: keyword
    },
    headers: NO_CACHE_HEADERS
  })
  const data = requireUsersPage(getResponseData(response, '用户数据待确认'))
  return data.records.find(item => item.username === keyword) || null
}

function buildGeneratedCredentialSummary(items: GeneratedCredential[]) {
  const visibleItems = items.slice(0, 10).map(item => `${item.username}：${item.initialPassword}`)
  const extraCount = Math.max(0, items.length - visibleItems.length)
  return [
    '以下账号已自动生成随机初始密码：',
    ...visibleItems,
    extraCount > 0 ? `另有 ${extraCount} 个账号未展开，请在关闭前完成记录。` : '',
    '关闭后将无法再次查看。'
  ].filter(Boolean).join('\n')
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
.status-note { margin-bottom: 12px; font-size: 12px; color: #f59e0b; }
.modal-status { margin-top: -4px; }
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
