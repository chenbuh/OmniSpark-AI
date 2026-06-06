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

interface LoginResult {
  token: string
  userInfo: AuthUserInfo
}

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

function normalizeLoginResult(payload: unknown): LoginResult {
  if (!isPlainObject(payload)) {
    throw new Error('登录结果待确认')
  }
  if (typeof payload.token !== 'string' || !payload.token.trim()) {
    throw new Error('登录结果待确认')
  }
  return {
    token: payload.token,
    userInfo: normalizeUserInfo(payload.userInfo)
  }
}

async function fetchCurrentUser() {
  const response = await request.get('/api/auth/me')
  return normalizeUserInfo(getResponseData(response, '用户信息待确认'))
}

function assertConfirmedLogin(result: LoginResult, confirmedUser: AuthUserInfo) {
  if (
    confirmedUser.id !== result.userInfo.id
    || confirmedUser.username !== result.userInfo.username
    || confirmedUser.nickname !== result.userInfo.nickname
    || confirmedUser.role !== result.userInfo.role
  ) {
    throw new Error('登录结果待确认')
  }
}

export const authApi = {
  // 登录
  async login(params: { username: string; password?: string; captchaTicket?: string }) {
    let loginResult: LoginResult | null = null
    try {
      const response = await request.post('/api/auth/login', {
        username: params.username,
        encryptedPassword: await encryptPassword(params.password || ''),
        captchaTicket: params.captchaTicket
      })
      loginResult = normalizeLoginResult(getResponseData(response, '登录结果待确认'))
      useUserStore().setSession(loginResult.userInfo, loginResult.token)
      const confirmedUser = await fetchCurrentUser()
      assertConfirmedLogin(loginResult, confirmedUser)
      useUserStore().setSession(confirmedUser, loginResult.token)
      await hydrateWorkspace()
      return {
        token: loginResult.token,
        userInfo: confirmedUser
      }
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
