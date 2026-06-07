import request from './request'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore } from '@/store/provider'
import { useAssetStore } from '@/store/asset'
import { encryptPassword } from '@/utils/passwordEncryption'

interface AuthUserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
}

interface LoginSuccessResult {
  type: 'success'
  token: string
  userInfo: AuthUserInfo
}

interface LoginRequiresTotpResult {
  type: 'totp'
  requiresTotp: true
  loginTicket: string
}

interface LoginRequiresTotpSetupResult {
  type: 'totp-setup'
  requiresTotpSetup: true
  setupTicket: string
  totpSecret: string
  totpOtpauthUrl: string
  totpIssuer: string
}

export type LoginFlowResult = LoginSuccessResult | LoginRequiresTotpResult | LoginRequiresTotpSetupResult
export type TotpSetupPayload = LoginRequiresTotpSetupResult

const DEVICE_ID_STORAGE_KEY = 'loginDeviceId'

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function clearSessionState() {
  useUserStore().logout()
  useTaskStore().clear()
  useProjectStore().clear()
  useModelProviderStore().clear()
  useAssetStore().clear()
}

async function hydrateWorkspace() {
  await Promise.allSettled([
    useProjectStore().refresh(),
    useTaskStore().refresh(),
    useModelProviderStore().refresh(),
    useAssetStore().refresh()
  ])
}

function normalizeUserInfo(payload: unknown): AuthUserInfo {
  if (!isPlainObject(payload)) {
    throw new Error('用户信息待确认')
  }
  const id = Number(payload.id)
  if (!Number.isFinite(id)) {
    throw new Error('用户信息待确认')
  }
  if (typeof payload.username !== 'string' || typeof payload.nickname !== 'string' || typeof payload.role !== 'string') {
    throw new Error('用户信息待确认')
  }
  return {
    id,
    username: payload.username,
    nickname: payload.nickname,
    avatar: typeof payload.avatar === 'string' ? payload.avatar : '',
    role: payload.role
  }
}

function normalizeLoginFlowResult(payload: unknown): LoginFlowResult {
  if (!isPlainObject(payload)) {
    throw new Error('登录结果待确认')
  }

  if (payload.requiresTotpSetup === true) {
    if (
      typeof payload.setupTicket !== 'string' || !payload.setupTicket.trim()
      || typeof payload.totpSecret !== 'string' || !payload.totpSecret.trim()
      || typeof payload.totpOtpauthUrl !== 'string' || !payload.totpOtpauthUrl.trim()
      || typeof payload.totpIssuer !== 'string' || !payload.totpIssuer.trim()
    ) {
      throw new Error('登录结果待确认')
    }
    return {
      type: 'totp-setup',
      requiresTotpSetup: true,
      setupTicket: payload.setupTicket,
      totpSecret: payload.totpSecret,
      totpOtpauthUrl: payload.totpOtpauthUrl,
      totpIssuer: payload.totpIssuer
    }
  }

  if (payload.requiresTotp === true) {
    if (typeof payload.loginTicket !== 'string' || !payload.loginTicket.trim()) {
      throw new Error('登录结果待确认')
    }
    return {
      type: 'totp',
      requiresTotp: true,
      loginTicket: payload.loginTicket
    }
  }

  if (typeof payload.token !== 'string' || !payload.token.trim()) {
    throw new Error('登录结果待确认')
  }
  return {
    type: 'success',
    token: payload.token,
    userInfo: normalizeUserInfo(payload.userInfo)
  }
}

async function fetchCurrentUser() {
  const response = await request.get('/api/auth/me')
  return normalizeUserInfo(getResponseData(response, '用户信息待确认'))
}

function assertConfirmedLogin(result: LoginSuccessResult, confirmedUser: AuthUserInfo) {
  if (
    confirmedUser.id !== result.userInfo.id
    || confirmedUser.username !== result.userInfo.username
    || confirmedUser.nickname !== result.userInfo.nickname
    || confirmedUser.role !== result.userInfo.role
  ) {
    throw new Error('登录结果待确认')
  }
}

function generateDeviceId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes).map(byte => byte.toString(16).padStart(2, '0')).join('')
}

function getDeviceId() {
  const stored = localStorage.getItem(DEVICE_ID_STORAGE_KEY)?.trim()
  if (stored) {
    return stored
  }
  const created = generateDeviceId()
  localStorage.setItem(DEVICE_ID_STORAGE_KEY, created)
  return created
}

async function confirmAndHydrateSession(result: LoginSuccessResult) {
  useUserStore().setSession(result.userInfo, result.token)
  const confirmedUser = await fetchCurrentUser()
  assertConfirmedLogin(result, confirmedUser)
  useUserStore().setSession(confirmedUser, result.token)
  await hydrateWorkspace()
  return {
    type: 'success' as const,
    token: result.token,
    userInfo: confirmedUser
  }
}

export const authApi = {
  // 登录
  async login(params: { username: string; password?: string; captchaTicket?: string; deviceId?: string }) {
    let loginResult: LoginSuccessResult | null = null
    try {
      const response = await request.post('/api/auth/login', {
        username: params.username,
        encryptedPassword: await encryptPassword(params.password || ''),
        captchaTicket: params.captchaTicket,
        deviceId: params.deviceId || getDeviceId()
      })
      const flowResult = normalizeLoginFlowResult(getResponseData(response, '登录结果待确认'))
      if (flowResult.type !== 'success') {
        clearSessionState()
        return flowResult
      }
      loginResult = flowResult
      return await confirmAndHydrateSession(loginResult)
    } catch (error) {
      if (loginResult?.token) {
        try {
          await request.post('/api/auth/logout')
        } catch {}
      }
      clearSessionState()
      throw error
    }
  },

  async completeTotpLogin(params: { loginTicket: string; totpCode: string }) {
    let loginResult: LoginSuccessResult | null = null
    try {
      const response = await request.post('/api/auth/login/totp', params)
      const flowResult = normalizeLoginFlowResult(getResponseData(response, '登录结果待确认'))
      if (flowResult.type !== 'success') {
        throw new Error('登录结果待确认')
      }
      loginResult = flowResult
      return await confirmAndHydrateSession(loginResult)
    } catch (error) {
      if (loginResult?.token) {
        try {
          await request.post('/api/auth/logout')
        } catch {}
      }
      clearSessionState()
      throw error
    }
  },

  async beginTotpReset() {
    const response = await request.post('/api/auth/totp/reset/begin')
    const flowResult = normalizeLoginFlowResult(getResponseData(response, '验证器重置结果待确认'))
    if (flowResult.type !== 'totp-setup') {
      throw new Error('验证器重置结果待确认')
    }
    return flowResult
  },

  async completeTotpSetup(params: { setupTicket: string; totpCode: string }) {
    let loginResult: LoginSuccessResult | null = null
    try {
      const response = await request.post('/api/auth/login/totp/setup', params)
      const flowResult = normalizeLoginFlowResult(getResponseData(response, '登录结果待确认'))
      if (flowResult.type !== 'success') {
        throw new Error('登录结果待确认')
      }
      loginResult = flowResult
      return await confirmAndHydrateSession(loginResult)
    } catch (error) {
      if (loginResult?.token) {
        try {
          await request.post('/api/auth/logout')
        } catch {}
      }
      clearSessionState()
      throw error
    }
  },

  async confirmTotpReset(params: { setupTicket: string; totpCode: string }) {
    const response = await request.post('/api/auth/totp/reset/confirm', params)
    return normalizeUserInfo(getResponseData(response, '验证器绑定结果待确认'))
  },

  // 注册
  async register(params: { username: string; password: string; nickname: string; captchaTicket?: string }) {
    const response = await request.post('/api/auth/register', {
      username: params.username,
      nickname: params.nickname,
      encryptedPassword: await encryptPassword(params.password),
      captchaTicket: params.captchaTicket
    })
    const createdUser = normalizeUserInfo(getResponseData(response, '注册结果待确认'))
    if (
      createdUser.username !== params.username.trim()
      || createdUser.nickname !== params.nickname.trim()
    ) {
      throw new Error('注册结果待确认')
    }
    return createdUser
  },

  // 退出
  async logout() {
    let res
    try {
      res = await request.post('/api/auth/logout')
    } finally {
      clearSessionState()
    }
    return res
  },

  // 获取当前登录用户
  async getMe() {
    const token = localStorage.getItem('satoken') || ''
    const userInfo = await fetchCurrentUser()
    useUserStore().setSession(userInfo, token)
    await hydrateWorkspace()
    return userInfo
  }
}
