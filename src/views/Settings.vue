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

          <template v-if="isAdmin">
            <n-divider style="margin: 20px 0 16px;" />
            <div class="totp-admin-header">
              <div>
                <div class="totp-admin-title">管理员验证器</div>
                <p class="totp-admin-hint">已登录后可在这里主动重置验证器绑定。旧手机令牌丢失或删除时，重新扫码即可。</p>
              </div>
              <n-button
                v-if="!totpResetState.setupTicket"
                tertiary
                type="primary"
                :loading="resettingTotp"
                @click="handleBeginTotpReset"
              >
                重置验证器绑定
              </n-button>
            </div>

            <div v-if="totpResetState.setupTicket" class="totp-reset-card">
              <div class="totp-qr-section">
                <div v-if="totpResetQrCodeUrl" class="totp-qr-box">
                  <img :src="totpResetQrCodeUrl" alt="TOTP 重置二维码" class="totp-qr-image" />
                </div>
                <div class="totp-qr-copy">
                  <span class="totp-qr-title">重新扫码绑定</span>
                  <span class="totp-qr-hint">请先删除旧令牌，再使用新的二维码重新绑定。</span>
                  <span class="totp-qr-hint">也支持复制密钥或绑定链接做手动导入。</span>
                </div>
              </div>

              <div class="totp-meta">
                <span>发行方</span>
                <strong>{{ totpResetState.issuer }}</strong>
              </div>
              <div class="totp-meta">
                <span>登录账号</span>
                <strong>{{ userStore.userInfo?.username }}</strong>
              </div>
              <div class="totp-secret-box">{{ totpResetState.secret }}</div>
              <div class="totp-actions">
                <n-button secondary type="primary" @click="copyText(totpResetState.secret, '密钥已复制')">复制密钥</n-button>
                <n-button secondary @click="copyText(totpResetState.otpauthUrl, '绑定链接已复制')">复制绑定链接</n-button>
              </div>

              <n-form-item label="动态验证码" style="margin-top: 16px;">
                <n-input
                  v-model:value="totpResetState.code"
                  placeholder="输入新验证器当前显示的 6 位动态码"
                  maxlength="6"
                  @keyup.enter="handleConfirmTotpReset"
                />
              </n-form-item>
              <div class="totp-actions">
                <n-button type="primary" :loading="confirmingTotpReset" @click="handleConfirmTotpReset">确认新绑定</n-button>
                <n-button :disabled="confirmingTotpReset" @click="resetTotpResetState">取消</n-button>
              </div>
            </div>
          </template>
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
        <div class="pagination-wrap" v-if="loginLogsTotal > logPageSize">
          <n-pagination
            v-model:page="logPage"
            :item-count="loginLogsTotal"
            :page-size="logPageSize"
            :page-slot="5"
            size="small"
            @update:page="loadLoginLogs"
          />
        </div>
      </template>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, type Ref } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { Pencil } from 'lucide-vue-next'
import QRCode from 'qrcode'
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
const isAdmin = computed(() => userStore.userInfo?.role?.toLowerCase() === 'admin')
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

const copyText = async (value: string, successText: string) => {
  if (!value.trim()) {
    message.error('没有可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    message.success(successText)
  } catch {
    message.error('复制失败，请手动复制')
  }
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

const resettingTotp = ref(false)
const confirmingTotpReset = ref(false)
const totpResetQrCodeUrl = ref('')
const totpResetState = reactive({
  setupTicket: '',
  secret: '',
  otpauthUrl: '',
  issuer: '',
  code: ''
})

const isValidTotpCode = (value: string) => /^\d{6}$/.test(value.trim())

const resetTotpResetState = () => {
  totpResetState.setupTicket = ''
  totpResetState.secret = ''
  totpResetState.otpauthUrl = ''
  totpResetState.issuer = ''
  totpResetState.code = ''
  totpResetQrCodeUrl.value = ''
}

const buildTotpResetQrCode = async (value: string) => {
  const normalized = value.trim()
  if (!normalized) {
    totpResetQrCodeUrl.value = ''
    return
  }
  try {
    totpResetQrCodeUrl.value = await QRCode.toDataURL(normalized, {
      errorCorrectionLevel: 'M',
      margin: 1,
      width: 220,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
  } catch {
    totpResetQrCodeUrl.value = ''
    message.error('重置二维码生成失败，请先使用密钥或绑定链接完成手动导入')
  }
}

const applyTotpSetupPayload = (payload: Awaited<ReturnType<typeof authApi.beginTotpReset>>) => {
  totpResetState.setupTicket = payload.setupTicket
  totpResetState.secret = payload.totpSecret
  totpResetState.otpauthUrl = payload.totpOtpauthUrl
  totpResetState.issuer = payload.totpIssuer
  totpResetState.code = ''
}

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

const handleBeginTotpReset = () => {
  dialog.warning({
    title: '确认重置验证器',
    content: '重置后旧手机上的动态验证码将不再作为新的绑定来源，请使用新二维码重新绑定后再继续使用。',
    positiveText: '开始重置',
    negativeText: '取消',
    onPositiveClick: async () => {
      resettingTotp.value = true
      try {
        const result = await authApi.beginTotpReset()
        applyTotpSetupPayload(result)
        message.success('新的验证器绑定二维码已生成，请扫码后输入动态验证码完成确认')
      } catch (err: unknown) {
        message.error(getErrorMessage(err, '重置验证器失败，请稍后重试'))
      } finally {
        resettingTotp.value = false
      }
    }
  })
}

async function handleConfirmTotpReset() {
  if (!totpResetState.setupTicket.trim()) {
    message.error('当前重置会话已失效，请重新开始')
    resetTotpResetState()
    return
  }
  if (!isValidTotpCode(totpResetState.code)) {
    message.error('请输入 6 位数字动态验证码')
    return
  }
  confirmingTotpReset.value = true
  try {
    await authApi.confirmTotpReset({
      setupTicket: totpResetState.setupTicket,
      totpCode: totpResetState.code.trim()
    })
    await authApi.getMe()
    resetTotpResetState()
    message.success('验证器已重置并重新绑定成功')
  } catch (err: unknown) {
    const errorMessage = getErrorMessage(err, '验证器绑定失败，请重新重置')
    message.error(errorMessage)
    totpResetState.code = ''
  } finally {
    confirmingTotpReset.value = false
  }
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
const latestLoginRecord = ref<LoginLogRecord | null>(null)
const loadingLogs = ref(true)
const logPage = ref(1)
const logPageSize = 8
const loginLogsTotal = ref(0)

const pagedLogs = computed(() => loginLogs.value || [])
const latestLoginLog = computed(() => latestLoginRecord.value)
const loginLogsCountLabel = computed(() => loginLogs.value === null ? '待确认' : `${loginLogsTotal.value} 条`)
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

function requireLoginLogPage(value: unknown) {
  if (!isPlainObject(value)) {
    throw new Error('登录记录待确认')
  }
  const records = value.records
  const total = Number(value.total)
  if (!Array.isArray(records) || !Number.isFinite(total) || total < 0) {
    throw new Error('登录记录待确认')
  }
  const normalizedRecords = normalizeLoginLogList(records)
  if (normalizedRecords.length > total) {
    throw new Error('登录记录待确认')
  }
  return {
    records: normalizedRecords,
    total
  }
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
  await loadLoginLogs()
})

watch(() => totpResetState.otpauthUrl, (value) => {
  void buildTotpResetQrCode(value)
})

async function loadLoginLogs() {
  try {
    loadingLogs.value = true
    const logsResponse = await request.get('/api/auth/login-logs', {
      params: { page: logPage.value, pageSize: logPageSize },
      headers: NO_CACHE_HEADERS
    })
    const data = requireLoginLogPage(getResponseData(logsResponse, '登录记录待确认'))
    loginLogs.value = data.records
    loginLogsTotal.value = data.total
    if (logPage.value === 1 || latestLoginRecord.value === null) {
      latestLoginRecord.value = data.records[0] || null
    }
  } catch {
    loginLogs.value = null
    latestLoginRecord.value = null
    loginLogsTotal.value = 0
  } finally {
    loadingLogs.value = false
  }
}

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

.totp-admin-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.totp-admin-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.totp-admin-hint {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-muted);
}

.totp-reset-card {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  background: rgba(127, 127, 127, 0.04);
}

.totp-qr-section {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.totp-qr-box {
  flex: 0 0 auto;
  padding: 10px;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.totp-qr-image {
  display: block;
  width: 132px;
  height: 132px;
  object-fit: contain;
}

.totp-qr-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.totp-qr-title {
  font-size: 15px;
  font-weight: 700;
}

.totp-qr-hint {
  font-size: 12px;
  line-height: 1.55;
  color: var(--text-muted);
}

.totp-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--text-secondary);
}

.totp-secret-box {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.68);
  color: #e2e8f0;
  font-family: "Consolas", "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
}

.totp-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.count { font-size: 12px; color: var(--text-muted); }

.log-table { background: transparent !important; }
.log-table th { background: rgba(128,128,128,0.02) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px; }
.log-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary); padding: 8px; font-size: 12px; }
.empty-cell { text-align: center; padding: 20px; color: var(--text-muted); }

.s-icon { width: 14px; height: 14px; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }

@media (max-width: 900px) {
  .totp-admin-header {
    flex-direction: column;
  }

  .totp-qr-section {
    flex-direction: column;
    align-items: stretch;
  }

  .totp-qr-box {
    align-self: center;
  }

  .totp-actions {
    flex-direction: column;
  }
}
</style>
