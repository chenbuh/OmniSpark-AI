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
              <th style="width:300px">操作</th>
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
                  <n-button size="tiny" tertiary @click="openUserProfile(u)">画像</n-button>
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

    <n-drawer v-model:show="profileVisible" :width="760" placement="right">
      <n-drawer-content :title="selectedProfile ? `${selectedProfile.username} 的用户画像` : '用户画像'" closable>
        <template v-if="profileLoading">
          <n-skeleton text :repeat="10" style="margin: 8px 0;" />
        </template>
        <template v-else-if="selectedProfile">
          <div class="profile-panel">
            <div class="profile-header-card">
              <div class="profile-header-main">
                <n-avatar
                  v-if="selectedProfile.avatar"
                  :src="selectedProfile.avatar"
                  :size="54"
                  round
                />
                <div v-else class="profile-avatar-fallback">{{ (selectedProfile.nickname || selectedProfile.username)?.[0] }}</div>
                <div class="profile-title-block">
                  <div class="profile-title-row">
                    <strong>{{ selectedProfile.nickname || selectedProfile.username }}</strong>
                    <n-tag size="small" :type="selectedProfile.status === 1 ? 'success' : 'warning'">
                      {{ selectedProfile.status === 1 ? '启用中' : '已禁用' }}
                    </n-tag>
                    <n-tag size="small" type="info">{{ selectedProfile.role || '-' }}</n-tag>
                    <n-tag size="small" :type="selectedProfile.totpEnabled === 1 ? 'success' : 'default'">
                      {{ selectedProfile.totpEnabled === 1 ? '已绑定动态验证码' : '未绑定动态验证码' }}
                    </n-tag>
                  </div>
                  <div class="profile-subtitle-row">
                    <span>用户名 {{ selectedProfile.username }}</span>
                    <span>ID #{{ selectedProfile.id }}</span>
                    <span>创建于 {{ formatFull(selectedProfile.createdAt) }}</span>
                  </div>
                </div>
              </div>
              <div class="profile-count-grid">
                <div class="summary-tile">
                  <span>登录记录</span>
                  <strong>{{ selectedProfile.totalLoginCount }}</strong>
                </div>
                <div class="summary-tile">
                  <span>审计轨迹</span>
                  <strong>{{ selectedProfile.totalAuditCount }}</strong>
                </div>
                <div class="summary-tile">
                  <span>访问日志</span>
                  <strong>{{ selectedProfile.totalAccessCount }}</strong>
                </div>
              </div>
            </div>

            <div class="profile-summary-grid">
              <div class="summary-card">
                <span class="summary-label">最近登录地区</span>
                <strong>{{ selectedProfile.latestLogin ? formatIpGeoSummary(selectedProfile.latestLogin.ipGeo) : '暂无登录记录' }}</strong>
                <p>{{ selectedProfile.latestLogin ? `${formatFull(selectedProfile.latestLogin.createdAt)} · ${selectedProfile.latestLogin.ip || '-'}` : '尚未记录到登录来源 IP。' }}</p>
              </div>
              <div class="summary-card">
                <span class="summary-label">最近操作轨迹</span>
                <strong>{{ selectedProfile.latestAudit ? formatAction(selectedProfile.latestAudit.action) : '暂无审计记录' }}</strong>
                <p>{{ selectedProfile.latestAudit ? `${formatFull(selectedProfile.latestAudit.createdAt)} · ${selectedProfile.latestAudit.detail || selectedProfile.latestAudit.ip || '-'}` : '尚未记录到后台操作轨迹。' }}</p>
              </div>
              <div class="summary-card">
                <span class="summary-label">最近访问来源</span>
                <strong>{{ selectedProfile.latestAccess ? formatIpGeoSummary(selectedProfile.latestAccess.ipGeo) : '暂无访问日志' }}</strong>
                <p>{{ selectedProfile.latestAccess ? `${formatFull(selectedProfile.latestAccess.createdAt)} · ${selectedProfile.latestAccess.clientIp || '-'} · ${selectedProfile.latestAccess.path || '-'}` : '尚未记录到接口访问来源。' }}</p>
              </div>
              <div class="summary-card">
                <span class="summary-label">常用 IP 数</span>
                <strong>{{ selectedProfile.commonIps.length }}</strong>
                <p>{{ selectedProfile.commonIps[0] ? `最近常用 IP：${selectedProfile.commonIps[0].ip}` : '暂无可归纳的常用 IP。' }}</p>
              </div>
            </div>

            <div class="profile-section">
              <div class="profile-section-head">
                <h3>常用 IP 画像</h3>
                <span>综合登录、审计与访问日志统计</span>
              </div>
              <div v-if="selectedProfile.commonIps.length > 0" class="ip-profile-list">
                <div v-for="item in selectedProfile.commonIps" :key="item.ip" class="ip-profile-card">
                  <div class="ip-profile-top">
                    <code>{{ item.ip || '-' }}</code>
                    <span>{{ formatIpGeoSummary(item.ipGeo) }}</span>
                  </div>
                  <div class="ip-profile-meta">
                    <span>{{ item.ipGeo?.isp || '运营商待确认' }}</span>
                    <span>最近出现 {{ formatFull(item.lastSeenAt) }}</span>
                  </div>
                  <div class="ip-profile-stats">
                    <span>登录 {{ item.loginCount }}</span>
                    <span>审计 {{ item.auditCount }}</span>
                    <span>访问 {{ item.accessCount }}</span>
                    <span>总计 {{ item.totalCount }}</span>
                  </div>
                  <div class="ip-profile-extra">
                    <span>{{ formatFlags(item.ipGeo) }}</span>
                    <span>{{ formatCoordinates(item.ipGeo) }}</span>
                  </div>
                </div>
              </div>
              <n-empty v-else description="暂无可展示的常用 IP 画像" />
            </div>

            <div class="profile-section">
              <n-tabs v-model:value="profileTab" type="segment" animated>
                <n-tab-pane name="logins" tab="最近登录">
                  <div v-if="selectedProfile.recentLoginLogs.length > 0" class="activity-list">
                    <div v-for="log in selectedProfile.recentLoginLogs" :key="`login-${log.id}`" class="activity-card">
                      <div class="activity-title-row">
                        <strong>{{ formatFull(log.createdAt) }}</strong>
                        <code>{{ log.ip || '-' }}</code>
                      </div>
                      <p>{{ formatIpGeoSummary(log.ipGeo) }}</p>
                      <div class="activity-meta-row">
                        <span>{{ log.ipGeo?.isp || '运营商待确认' }}</span>
                        <span class="break-all">{{ log.userAgent || '-' }}</span>
                      </div>
                    </div>
                  </div>
                  <n-empty v-else description="暂无登录记录" />
                </n-tab-pane>
                <n-tab-pane name="audits" tab="最近操作">
                  <div v-if="selectedProfile.recentAuditLogs.length > 0" class="activity-list">
                    <div v-for="log in selectedProfile.recentAuditLogs" :key="`audit-${log.id}`" class="activity-card">
                      <div class="activity-title-row">
                        <strong>{{ formatAction(log.action) }}</strong>
                        <span>{{ formatFull(log.createdAt) }}</span>
                      </div>
                      <p class="break-all">{{ log.detail || '暂无详细说明' }}</p>
                      <div class="activity-meta-row">
                        <code>{{ log.ip || '-' }}</code>
                        <span>{{ formatIpGeoSummary(log.ipGeo) }}</span>
                        <span>{{ log.resourceType || '-' }}{{ log.resourceId != null ? ` #${log.resourceId}` : '' }}</span>
                      </div>
                    </div>
                  </div>
                  <n-empty v-else description="暂无审计轨迹" />
                </n-tab-pane>
                <n-tab-pane name="access" tab="访问日志">
                  <div v-if="selectedProfile.recentAccessLogs.length > 0" class="activity-list">
                    <div v-for="log in selectedProfile.recentAccessLogs" :key="`access-${log.id}`" class="activity-card">
                      <div class="activity-title-row">
                        <strong>{{ log.method || 'HTTP' }} {{ log.statusCode }}</strong>
                        <span>{{ formatFull(log.createdAt) }}</span>
                      </div>
                      <p class="break-all">{{ log.path || '-' }}</p>
                      <div class="activity-meta-row">
                        <code>{{ log.clientIp || '-' }}</code>
                        <span>{{ formatIpGeoSummary(log.ipGeo) }}</span>
                        <span>{{ log.durationMs }}ms</span>
                      </div>
                    </div>
                  </div>
                  <n-empty v-else description="暂无访问日志" />
                </n-tab-pane>
              </n-tabs>
            </div>
          </div>
        </template>
        <n-empty v-else description="用户画像待确认，请稍后重试。" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useMessage, useDialog } from 'naive-ui'
import { Search, Download, Upload, Plus, RefreshCw } from 'lucide-vue-next'
import request, { API_BASE_URL } from '@/api/request'
import { formatIpGeoSummary, normalizeIpGeoInfo, type IpGeoInfo } from '@/utils/ipGeo'
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

type UserLoginLogRecord = {
  id: number
  userId: number
  username: string
  ip: string
  userAgent: string
  createdAt: string
  ipGeo: IpGeoInfo | null
}

type UserAuditLogRecord = {
  id: number
  userId: number | null
  username: string
  action: string
  resourceType: string
  resourceId: number | null
  detail: string
  ip: string
  createdAt: string
  ipGeo: IpGeoInfo | null
}

type UserAccessLogRecord = {
  id: number
  userId: number | null
  apiKeyId: number | null
  clientIp: string
  userAgent: string
  method: string
  path: string
  queryString: string
  statusCode: number
  durationMs: number | null
  rateLimited: number | null
  riskReason: string
  createdAt: string
  ipGeo: IpGeoInfo | null
}

type UserIpProfileRecord = {
  ip: string
  ipGeo: IpGeoInfo | null
  loginCount: number
  auditCount: number
  accessCount: number
  totalCount: number
  lastSeenAt: string
}

type AdminUserProfile = {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
  status: number
  totpEnabled: number
  createdAt: string
  totalLoginCount: number
  totalAuditCount: number
  totalAccessCount: number
  latestLogin: UserLoginLogRecord | null
  latestAudit: UserAuditLogRecord | null
  latestAccess: UserAccessLogRecord | null
  commonIps: UserIpProfileRecord[]
  recentLoginLogs: UserLoginLogRecord[]
  recentAuditLogs: UserAuditLogRecord[]
  recentAccessLogs: UserAccessLogRecord[]
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
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileTab = ref<'logins' | 'audits' | 'access'>('logins')
const selectedProfile = ref<AdminUserProfile | null>(null)

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

function normalizeOptionalNumber(value: unknown) {
  if (value == null || value === '') {
    return null
  }
  const normalized = Number(value)
  return Number.isFinite(normalized) ? normalized : null
}

function requirePositiveNumber(value: unknown, errorMessage: string) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized <= 0) {
    throw new Error(errorMessage)
  }
  return normalized
}

function requireNonNegativeNumber(value: unknown, errorMessage: string) {
  const normalized = Number(value)
  if (!Number.isFinite(normalized) || normalized < 0) {
    throw new Error(errorMessage)
  }
  return normalized
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

function normalizeLoginLogRecord(value: unknown): UserLoginLogRecord {
  if (!isPlainObject(value)) {
    throw new Error('用户画像待确认')
  }
  const id = requirePositiveNumber(value.id, '用户画像待确认')
  const userId = requirePositiveNumber(value.userId, '用户画像待确认')
  const username = normalizeOptionalText(value.username)
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!username || !createdAt) {
    throw new Error('用户画像待确认')
  }
  return {
    id,
    userId,
    username,
    ip: normalizeOptionalText(value.ip),
    userAgent: normalizeOptionalText(value.userAgent),
    createdAt,
    ipGeo: normalizeIpGeoInfo(value.ipGeo)
  }
}

function normalizeAuditLogRecord(value: unknown): UserAuditLogRecord {
  if (!isPlainObject(value)) {
    throw new Error('用户画像待确认')
  }
  const id = requirePositiveNumber(value.id, '用户画像待确认')
  const action = normalizeOptionalText(value.action)
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!action || !createdAt) {
    throw new Error('用户画像待确认')
  }
  return {
    id,
    userId: normalizeOptionalNumber(value.userId),
    username: normalizeOptionalText(value.username),
    action,
    resourceType: normalizeOptionalText(value.resourceType),
    resourceId: normalizeOptionalNumber(value.resourceId),
    detail: normalizeOptionalText(value.detail),
    ip: normalizeOptionalText(value.ip),
    createdAt,
    ipGeo: normalizeIpGeoInfo(value.ipGeo)
  }
}

function normalizeAccessLogRecord(value: unknown): UserAccessLogRecord {
  if (!isPlainObject(value)) {
    throw new Error('用户画像待确认')
  }
  const id = requirePositiveNumber(value.id, '用户画像待确认')
  const statusCode = requireNonNegativeNumber(value.statusCode, '用户画像待确认')
  const createdAt = normalizeOptionalText(value.createdAt)
  if (!createdAt) {
    throw new Error('用户画像待确认')
  }
  return {
    id,
    userId: normalizeOptionalNumber(value.userId),
    apiKeyId: normalizeOptionalNumber(value.apiKeyId),
    clientIp: normalizeOptionalText(value.clientIp),
    userAgent: normalizeOptionalText(value.userAgent),
    method: normalizeOptionalText(value.method),
    path: normalizeOptionalText(value.path),
    queryString: normalizeOptionalText(value.queryString),
    statusCode,
    durationMs: normalizeOptionalNumber(value.durationMs),
    rateLimited: normalizeOptionalNumber(value.rateLimited),
    riskReason: normalizeOptionalText(value.riskReason),
    createdAt,
    ipGeo: normalizeIpGeoInfo(value.ipGeo)
  }
}

function normalizeUserIpProfileRecord(value: unknown): UserIpProfileRecord {
  if (!isPlainObject(value)) {
    throw new Error('用户画像待确认')
  }
  const ip = normalizeOptionalText(value.ip)
  const totalCount = requireNonNegativeNumber(value.totalCount, '用户画像待确认')
  const lastSeenAt = normalizeOptionalText(value.lastSeenAt)
  if (!ip || !lastSeenAt) {
    throw new Error('用户画像待确认')
  }
  return {
    ip,
    ipGeo: normalizeIpGeoInfo(value.ipGeo),
    loginCount: requireNonNegativeNumber(value.loginCount, '用户画像待确认'),
    auditCount: requireNonNegativeNumber(value.auditCount, '用户画像待确认'),
    accessCount: requireNonNegativeNumber(value.accessCount, '用户画像待确认'),
    totalCount,
    lastSeenAt
  }
}

function requireAdminUserProfile(value: unknown): AdminUserProfile {
  if (!isPlainObject(value)) {
    throw new Error('用户画像待确认')
  }
  const id = requirePositiveNumber(value.id, '用户画像待确认')
  const username = normalizeOptionalText(value.username)
  const role = normalizeOptionalText(value.role)
  const createdAt = normalizeOptionalText(value.createdAt)
  const status = normalizeUserStatus(value.status)
  const totpEnabled = normalizeUserStatus(value.totpEnabled) ?? 0
  if (!username || !role || !createdAt || status == null) {
    throw new Error('用户画像待确认')
  }
  const commonIpsRaw = Array.isArray(value.commonIps) ? value.commonIps : []
  const recentLoginLogsRaw = Array.isArray(value.recentLoginLogs) ? value.recentLoginLogs : []
  const recentAuditLogsRaw = Array.isArray(value.recentAuditLogs) ? value.recentAuditLogs : []
  const recentAccessLogsRaw = Array.isArray(value.recentAccessLogs) ? value.recentAccessLogs : []
  return {
    id,
    username,
    nickname: normalizeOptionalText(value.nickname),
    avatar: normalizeOptionalText(value.avatar),
    role,
    status,
    totpEnabled,
    createdAt,
    totalLoginCount: requireNonNegativeNumber(value.totalLoginCount, '用户画像待确认'),
    totalAuditCount: requireNonNegativeNumber(value.totalAuditCount, '用户画像待确认'),
    totalAccessCount: requireNonNegativeNumber(value.totalAccessCount, '用户画像待确认'),
    latestLogin: value.latestLogin == null ? null : normalizeLoginLogRecord(value.latestLogin),
    latestAudit: value.latestAudit == null ? null : normalizeAuditLogRecord(value.latestAudit),
    latestAccess: value.latestAccess == null ? null : normalizeAccessLogRecord(value.latestAccess),
    commonIps: commonIpsRaw.map(item => normalizeUserIpProfileRecord(item)),
    recentLoginLogs: recentLoginLogsRaw.map(item => normalizeLoginLogRecord(item)),
    recentAuditLogs: recentAuditLogsRaw.map(item => normalizeAuditLogRecord(item)),
    recentAccessLogs: recentAccessLogsRaw.map(item => normalizeAccessLogRecord(item))
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

async function openUserProfile(user: AdminUserRecord) {
  profileVisible.value = true
  profileLoading.value = true
  profileTab.value = 'logins'
  selectedProfile.value = null
  try {
    const response = await request.get<unknown>(`/api/admin/users/${user.id}/profile`, {
      headers: NO_CACHE_HEADERS
    })
    const profile = requireAdminUserProfile(getResponseData(response, '用户画像待确认'))
    if (profile.id !== user.id || profile.username !== user.username) {
      throw new Error('用户画像待确认')
    }
    selectedProfile.value = profile
  } catch (err: unknown) {
    profileVisible.value = false
    message.error(getErrorMessage(err, '加载用户画像失败'))
  } finally {
    profileLoading.value = false
  }
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

function formatCoordinates(ipGeo: IpGeoInfo | null) {
  if (ipGeo?.latitude == null || ipGeo?.longitude == null) {
    return '-'
  }
  return `${ipGeo.latitude.toFixed(4)}, ${ipGeo.longitude.toFixed(4)}`
}

function formatFlags(ipGeo: IpGeoInfo | null) {
  if (!ipGeo) return '-'
  const labels = [
    ipGeo.privateNetwork ? '内网/保留地址' : '',
    ipGeo.proxy ? '代理' : '',
    ipGeo.vpn ? 'VPN' : '',
    ipGeo.tor ? 'Tor' : '',
    ipGeo.hosting ? '机房' : ''
  ].filter(Boolean)
  return labels.length > 0 ? labels.join(' / ') : '-'
}

function formatAction(action: string) {
  if (!action) return '-'
  return action.replace(/_/g, ' ')
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
.profile-panel { display: flex; flex-direction: column; gap: 18px; }
.profile-header-card { display: flex; flex-direction: column; gap: 16px; padding: 18px; border-radius: 16px; background: rgba(15, 23, 42, 0.72); border: 1px solid var(--border-color); }
.profile-header-main { display: flex; gap: 14px; align-items: center; }
.profile-avatar-fallback { width: 54px; height: 54px; border-radius: 50%; background: rgba(128,128,128,0.16); color: var(--text-primary); display: flex; align-items: center; justify-content: center; font-size: 20px; font-weight: 700; flex-shrink: 0; }
.profile-title-block { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.profile-title-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.profile-title-row strong { font-size: 20px; color: var(--text-primary); }
.profile-subtitle-row { display: flex; flex-wrap: wrap; gap: 12px; color: var(--text-muted); font-size: 12px; }
.profile-count-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.summary-tile { padding: 14px; border-radius: 14px; background: rgba(148, 163, 184, 0.08); display: flex; flex-direction: column; gap: 8px; }
.summary-tile span { font-size: 12px; color: #94a3b8; }
.summary-tile strong { font-size: 24px; color: #f8fafc; }
.profile-summary-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.summary-card { padding: 16px; border-radius: 14px; background: rgba(15, 23, 42, 0.52); border: 1px solid var(--border-light); display: flex; flex-direction: column; gap: 8px; }
.summary-card strong { font-size: 16px; color: var(--text-primary); }
.summary-card p { margin: 0; font-size: 12px; line-height: 1.6; color: var(--text-muted); }
.summary-label { font-size: 12px; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.04em; }
.profile-section { padding: 16px; border-radius: 16px; background: rgba(15, 23, 42, 0.48); border: 1px solid var(--border-color); }
.profile-section-head { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; margin-bottom: 14px; }
.profile-section-head h3 { margin: 0; font-size: 16px; color: var(--text-primary); }
.profile-section-head span { font-size: 12px; color: var(--text-muted); }
.ip-profile-list { display: flex; flex-direction: column; gap: 12px; }
.ip-profile-card { padding: 14px; border-radius: 14px; background: rgba(148, 163, 184, 0.08); display: flex; flex-direction: column; gap: 10px; }
.ip-profile-top { display: flex; justify-content: space-between; gap: 12px; align-items: center; flex-wrap: wrap; color: var(--text-primary); }
.ip-profile-meta, .ip-profile-stats, .ip-profile-extra { display: flex; flex-wrap: wrap; gap: 12px; font-size: 12px; color: var(--text-muted); }
.activity-list { display: flex; flex-direction: column; gap: 12px; margin-top: 12px; }
.activity-card { padding: 14px; border-radius: 14px; background: rgba(148, 163, 184, 0.08); display: flex; flex-direction: column; gap: 10px; }
.activity-card p { margin: 0; color: var(--text-secondary); line-height: 1.6; }
.activity-title-row { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: center; color: var(--text-primary); }
.activity-meta-row { display: flex; flex-wrap: wrap; gap: 12px; font-size: 12px; color: var(--text-muted); }
.break-all { word-break: break-all; }

@media (max-width: 900px) {
  .profile-count-grid,
  .profile-summary-grid { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .profile-header-main { align-items: flex-start; }
  .profile-subtitle-row,
  .ip-profile-top,
  .activity-title-row { flex-direction: column; align-items: flex-start; }
}
</style>
