<template>
  <div class="settings-container">
    <div class="page-header">
      <h2>系统配置设置 (Settings)</h2>
      <p class="subtitle">管理您的个人信息、安全设置与系统偏好。</p>
    </div>

    <n-row :gutter="20">
      <!-- 个人信息卡 -->
      <n-col :span="12">
        <n-card title="个人身份管理" class="glass-card" :bordered="false">
          <div class="profile-box">
            <n-avatar round :size="80" :src="userStore.userInfo?.avatar" class="avatar" fallback-src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 80 80'%3E%3Crect fill='%23374151' width='80' height='80' rx='40'/%3E%3Ctext x='40' y='50' text-anchor='middle' fill='%239ca3af' font-size='36'%3E{{ (userStore.userInfo?.nickname || userStore.userInfo?.username)?.[0] }}%3C/text%3E%3C/svg%3E" />
            <div class="profile-meta">
              <div class="nickname-row">
                <template v-if="editingProfile">
                  <n-input v-model:value="editNickname" size="small" :maxlength="30" style="width:180px;" @keyup.enter="saveProfile" @keyup.escape="cancelProfileEdit" />
                  <n-button size="tiny" type="primary" @click="saveProfile">保存</n-button>
                  <n-button size="tiny" @click="cancelProfileEdit">取消</n-button>
                </template>
                <template v-else>
                  <span class="nickname">{{ userStore.userInfo?.nickname }}</span>
                  <n-button size="tiny" quaternary @click="startProfileEdit">
                    <template #icon><Pencil class="s-icon" /></template>
                  </n-button>
                </template>
              </div>
              <span class="role-badge" :class="userStore.userInfo?.role">
                {{ displayRoleLabel }}
              </span>
              <span class="username">系统账号: {{ userStore.userInfo?.username }}</span>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 账号概览 -->
      <n-col :span="12">
        <n-card title="账号与登录概览" class="glass-card" :bordered="false">
          <div class="summary-grid">
            <div class="summary-item">
              <span class="summary-label">当前角色</span>
              <span class="summary-value">{{ displayRoleLabel }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">登录记录</span>
              <span class="summary-value">{{ loginLogsCountLabel }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">最近登录时间</span>
              <span class="summary-value">{{ latestLoginTime }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">最近登录 IP</span>
              <span class="summary-value">{{ latestLoginIp }}</span>
            </div>
          </div>
        </n-card>
      </n-col>
    </n-row>

    <n-row :gutter="20" style="margin-top:20px;">
      <!-- 界面主题 -->
      <n-col :span="12">
        <n-card title="界面主题" class="glass-card" :bordered="false">
          <div class="theme-switch-box">
            <div class="theme-option" :class="{ active: isDark }" @click="setTheme(true)">
              <div class="theme-preview dark-preview"></div>
              <span>暗黑模式</span>
            </div>
            <div class="theme-option" :class="{ active: !isDark }" @click="setTheme(false)">
              <div class="theme-preview light-preview"></div>
              <span>亮色模式</span>
            </div>
          </div>
        </n-card>
      </n-col>

      <!-- 修改密码 -->
      <n-col :span="12">
        <n-card title="安全设置" class="glass-card" :bordered="false">
          <n-form label-placement="top" size="medium">
            <n-form-item label="当前密码" required>
              <n-input v-model:value="passwordForm.oldPassword" type="password" show-password-on="click" placeholder="输入当前密码" />
            </n-form-item>
            <n-form-item label="新密码" required>
              <n-input v-model:value="passwordForm.newPassword" type="password" show-password-on="click" :placeholder="PASSWORD_REQUIREMENT_TEXT" :maxlength="60" />
            </n-form-item>
            <n-form-item label="确认新密码" required>
              <n-input v-model:value="passwordForm.confirmPassword" type="password" show-password-on="click" placeholder="再次输入新密码" :maxlength="60" />
            </n-form-item>
            <n-row :gutter="8" justify="end">
              <n-button type="primary" @click="handleChangePassword" :loading="changingPassword">修改密码</n-button>
            </n-row>
          </n-form>
        </n-card>
      </n-col>
    </n-row>

    <!-- 登录历史 -->
    <n-card title="登录历史" class="glass-card" :bordered="false" style="margin-top:20px;">
      <template #header-extra>
        <span class="count" v-if="!loadingLogs">共 {{ loginLogsCountLabel }}</span>
      </template>

      <template v-if="loadingLogs">
        <n-skeleton text :repeat="3" style="margin:8px 0;" />
      </template>
      <template v-else>
        <n-table :single-line="false" class="log-table">
          <thead>
            <tr><th style="width:140px">IP</th><th>User Agent</th><th style="width:170px">时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="log in pagedLogs" :key="log.id">
              <td><code>{{ log.ip || '-' }}</code></td>
              <td><n-ellipsis :line-clamp="1" :tooltip="true">{{ log.userAgent || '-' }}</n-ellipsis></td>
              <td>{{ formatFull(log.createdAt) }}</td>
            </tr>
            <tr v-if="loginLogs !== null && pagedLogs.length === 0"><td colspan="3" class="empty-cell">暂无登录记录</td></tr>
            <tr v-else-if="loginLogs === null"><td colspan="3" class="empty-cell">登录记录待确认，请稍后重试。</td></tr>
          </tbody>
        </n-table>
        <div class="pagination-wrap" v-if="(loginLogs?.length || 0) > logPageSize">
          <n-pagination
            v-model:page="logPage"
            :page-count="logPageCount"
            :page-size="logPageSize"
            :page-slot="5"
            size="small"
          />
        </div>
      </template>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, type Ref } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { Pencil } from 'lucide-vue-next'
import { useUserStore } from '@/store/user'
import request from '@/api/request'
import { authApi } from '@/api/auth'
import { PASSWORD_REQUIREMENT_TEXT, validatePasswordStrength } from '@/utils/password'
import { encryptPassword } from '@/utils/passwordEncryption'
import { formatUserRole } from '@/utils/role'

const message = useMessage()
const dialog = useDialog()
const userStore = useUserStore()
const displayRoleLabel = computed(() => formatUserRole(userStore.userInfo?.role))
const NO_CACHE_HEADERS = { 'X-No-Cache': '1' }

type ThemeWindow = Window & {
  __toggleTheme?: () => void
  __isDark?: Ref<boolean>
}

const getThemeWindow = (): ThemeWindow => window as ThemeWindow

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
  if (error instanceof Error && error.message.trim()) {
    return error.message
  }
  return fallback
}

// ===== 主题 =====
const isDark = ref(getThemeWindow().__isDark?.value !== false)
const setTheme = (dark: boolean) => {
  isDark.value = dark
  if (getThemeWindow().__isDark?.value !== dark) {
    getThemeWindow().__toggleTheme?.()
  }
}

// ===== 个人资料编辑 =====
const editingProfile = ref(false)
const editNickname = ref('')

function startProfileEdit() {
  editNickname.value = userStore.userInfo?.nickname || ''
  editingProfile.value = true
}

function cancelProfileEdit() {
  editingProfile.value = false
  editNickname.value = ''
}

async function saveProfile() {
  const name = editNickname.value.trim()
  if (!name) { message.error('昵称不能为空'); return }
  try {
    const response = await request.put(`/api/auth/profile?nickname=${encodeURIComponent(name)}`)
    const data = requireProfileUser(getResponseData(response, '昵称更新结果待确认'))
    if (data.nickname !== name) {
      throw new Error('昵称更新结果待确认')
    }
    const confirmedProfile = await authApi.getMe()
    if (confirmedProfile.nickname !== name || confirmedProfile.id !== data.id || confirmedProfile.username !== data.username) {
      throw new Error('昵称更新结果待确认')
    }
    message.success('昵称已更新')
    editingProfile.value = false
  } catch (err: unknown) {
    message.error(getErrorMessage(err, '更新失败'))
  }
}

// ===== 修改密码 =====
const changingPassword = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

async function handleChangePassword() {
  if (!passwordForm.oldPassword) { message.error('请输入当前密码'); return }
  if (!passwordForm.newPassword) { message.error('请输入新密码'); return }
  const passwordError = validatePasswordStrength(passwordForm.newPassword, userStore.userInfo?.username)
  if (passwordError) { message.error(passwordError); return }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) { message.error('两次输入的新密码不一致'); return }

  dialog.warning({
    title: '确认修改密码',
    content: '修改密码后当前会话不会失效，下次登录请使用新密码。',
    positiveText: '确认修改',
    negativeText: '取消',
    onPositiveClick: async () => {
      changingPassword.value = true
      try {
        const params = new URLSearchParams({
          encryptedOldPassword: await encryptPassword(passwordForm.oldPassword),
          encryptedNewPassword: await encryptPassword(passwordForm.newPassword)
        })
        const response = await request.put('/api/auth/password', params.toString(), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        })
        const changed = requirePasswordChangeResult(getResponseData(response, '密码修改结果待确认'), userStore.userInfo?.id)
        const confirmedProfile = await authApi.getMe()
        if (confirmedProfile.id !== changed.userId) {
          throw new Error('密码修改结果待确认')
        }
        message.success('密码已修改')
        passwordForm.oldPassword = ''
        passwordForm.newPassword = ''
        passwordForm.confirmPassword = ''
      } catch (err: unknown) {
        message.error(getErrorMessage(err, '修改失败'))
      } finally {
        changingPassword.value = false
      }
    }
  })
}



// ===== 登录历史 =====
type LoginLogRecord = {
  id: number
  userId: number
  username: string
  ip: string
  userAgent: string
  createdAt: string
}

const loginLogs = ref<LoginLogRecord[] | null>(null)
const loadingLogs = ref(true)
const logPage = ref(1)
const logPageSize = 8

const logPageCount = computed(() => Math.max(1, Math.ceil((loginLogs.value?.length || 0) / logPageSize)))
const pagedLogs = computed(() => {
  const start = (logPage.value - 1) * logPageSize
  return (loginLogs.value || []).slice(start, start + logPageSize)
})
const latestLoginLog = computed(() => loginLogs.value?.[0] || null)
const loginLogsCountLabel = computed(() => loginLogs.value === null ? '待确认' : `${loginLogs.value.length} 条`)
const latestLoginTime = computed(() => {
  if (loginLogs.value === null) {
    return '待确认'
  }
  return formatFull(latestLoginLog.value?.createdAt || '')
})
const latestLoginIp = computed(() => latestLoginLog.value ? (latestLoginLog.value.ip || '-') : (loginLogs.value === null ? '待确认' : '-'))

function getLogTimestamp(dateStr: string): number {
  const timestamp = Date.parse(String(dateStr || ''))
  return Number.isNaN(timestamp) ? 0 : timestamp
}

function requireLoginLogRecord(value: unknown): LoginLogRecord {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('登录记录待确认')
  }
  const record = value as Record<string, unknown>
  const id = Number(record.id)
  const userId = Number(record.userId)
  const username = typeof record.username === 'string' ? record.username.trim() : ''
  const createdAt = typeof record.createdAt === 'string' ? record.createdAt.trim() : ''
  if (!Number.isFinite(id) || id <= 0 || !Number.isFinite(userId) || userId <= 0 || !username || !createdAt) {
    throw new Error('登录记录待确认')
  }
  return {
    id,
    userId,
    username,
    ip: typeof record.ip === 'string' ? record.ip.trim() : '',
    userAgent: typeof record.userAgent === 'string' ? record.userAgent.trim() : '',
    createdAt
  }
}

function normalizeLoginLogList(value: unknown) {
  if (!Array.isArray(value)) {
    throw new Error('登录记录待确认')
  }
  const seenIds = new Set<number>()
  return value
    .map(item => requireLoginLogRecord(item))
    .filter(item => {
      if (seenIds.has(item.id)) {
        return false
      }
      seenIds.add(item.id)
      return true
    })
    .sort((a, b) => getLogTimestamp(b.createdAt) - getLogTimestamp(a.createdAt))
}

function requireProfileUser(payload: unknown) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('昵称更新结果待确认')
  }
  const record = payload as Record<string, unknown>
  const id = Number(record.id)
  if (!Number.isFinite(id)) {
    throw new Error('昵称更新结果待确认')
  }
  if (typeof record.username !== 'string' || typeof record.nickname !== 'string' || typeof record.role !== 'string') {
    throw new Error('昵称更新结果待确认')
  }
  return {
    id,
    username: record.username,
    nickname: record.nickname,
    avatar: typeof record.avatar === 'string' ? record.avatar : '',
    role: record.role
  }
}

function requirePasswordChangeResult(payload: unknown, expectedUserId?: number) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('密码修改结果待确认')
  }
  const record = payload as Record<string, unknown>
  const userId = Number(record.userId)
  if (!Number.isFinite(userId) || userId <= 0) {
    throw new Error('密码修改结果待确认')
  }
  if (expectedUserId && userId !== expectedUserId) {
    throw new Error('密码修改结果待确认')
  }
  if (record.changed !== true) {
    throw new Error('密码修改结果待确认')
  }
  if (typeof record.updatedAt !== 'string' || !record.updatedAt.trim()) {
    throw new Error('密码修改结果待确认')
  }
  return {
    userId,
    updatedAt: record.updatedAt
  }
}

// ===== 生命周期 =====
onMounted(async () => {
  try {
    loadingLogs.value = true
    const logsResponse = await request.get('/api/auth/login-logs?limit=100', {
      headers: NO_CACHE_HEADERS
    })
    loginLogs.value = normalizeLoginLogList(getResponseData(logsResponse, '登录记录待确认'))
  } catch {
    loginLogs.value = null
  } finally {
    loadingLogs.value = false
  }
})

// ===== 工具 =====
function formatFull(dateStr: string): string {
  if (!dateStr) return '-'
  return dateStr.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.settings-container { padding-bottom: 40px; }

.page-header { margin-bottom: 24px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; color: var(--text-primary); }
.subtitle { font-size: 13px; color: var(--text-muted); margin: 0; }

.glass-card { backdrop-filter: blur(16px); border: 1px solid var(--border-color) !important; border-radius: 16px !important; }

.profile-box { display: flex; align-items: center; gap: 20px; }
.avatar { border: 2px solid var(--border-color); box-shadow: 0 4px 15px rgba(0,0,0,0.3); }
.profile-meta { display: flex; flex-direction: column; gap: 6px; }

.nickname-row { display: flex; align-items: center; gap: 6px; }
.nickname { font-size: 20px; font-weight: 700; color: var(--text-primary); }

.username { font-size: 12px; color: var(--text-muted); }

.role-badge { font-size: 10px; padding: 2px 6px; border-radius: 4px; width: fit-content; font-weight: 600; }
.role-badge.admin { background-color: rgba(139, 92, 246, 0.15); color: #a78bfa; }
.role-badge.user { background-color: rgba(59, 130, 246, 0.15); color: #60a5fa; }

.summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: rgba(127, 127, 127, 0.04);
}
.summary-label { font-size: 12px; color: var(--text-muted); }
.summary-value { font-size: 15px; font-weight: 600; color: var(--text-primary); word-break: break-all; }

.theme-switch-box { display: flex; gap: 16px; justify-content: center; padding: 12px 0; }
.theme-option {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  cursor: pointer; padding: 12px; border-radius: 12px;
  border: 2px solid var(--border-color); transition: all .3s; width: 120px;
}
.theme-option:hover { border-color: #10b981; }
.theme-option.active { border-color: #10b981; background: rgba(16,185,129,0.06); }
.theme-option span { font-size: 13px; color: var(--text-secondary); }
.theme-preview { width: 80px; height: 50px; border-radius: 8px; border: 1px solid var(--border-color); }
.dark-preview { background: linear-gradient(135deg, #05070c 50%, #0f172a 50%); }
.light-preview { background: linear-gradient(135deg, #ffffff 50%, #f0f0f0 50%); }

.count { font-size: 12px; color: var(--text-muted); }

.log-table { background: transparent !important; }
.log-table th { background: rgba(128,128,128,0.02) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px; }
.log-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary); padding: 8px; font-size: 12px; }
.empty-cell { text-align: center; padding: 20px; color: var(--text-muted); }

.s-icon { width: 14px; height: 14px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
