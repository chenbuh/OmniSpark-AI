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
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('用户信息待确认')
  }
  const record = payload as Record<string, unknown>
  const id = Number(record.id)
  if (!Number.isFinite(id)) {
    throw new Error('用户信息待确认')
  }
  if (typeof record.username !== 'string' || typeof record.nickname !== 'string' || typeof record.role !== 'string') {
    throw new Error('用户信息待确认')
  }
  return {
    id,
    username: record.username,
    nickname: record.nickname,
    avatar: typeof record.avatar === 'string' ? record.avatar : '',
    role: record.role
  }
}

function normalizeLoginResult(payload: unknown): LoginResult {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error('登录结果待确认')
  }
  const record = payload as Record<string, unknown>
  if (typeof record.token !== 'string' || !record.token.trim()) {
    throw new Error('登录结果待确认')
  }
  return {
    token: record.token,
    userInfo: normalizeUserInfo(record.userInfo)
  }
}

async function fetchCurrentUser() {
  const res = await request.get('/api/auth/me')
  return normalizeUserInfo((res as any).data)
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
      const res = await request.post('/api/auth/login', {
        username: params.username,
        encryptedPassword: await encryptPassword(params.password || ''),
        captchaTicket: params.captchaTicket
      })
      loginResult = normalizeLoginResult((res as any).data)
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
    const res = await request.post('/api/auth/register', {
      username: params.username,
      nickname: params.nickname,
      encryptedPassword: await encryptPassword(params.password),
      captchaTicket: params.captchaTicket
    })
    const createdUser = normalizeUserInfo((res as any).data)
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
