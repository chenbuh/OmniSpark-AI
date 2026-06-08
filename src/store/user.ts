import { defineStore } from 'pinia'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string // 'admin' | 'user'
  totpEnabled: boolean
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function normalizeStoredText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function parseStoredUserInfo(value: string | null): UserInfo | null {
  if (!value) {
    return null
  }
  try {
    const parsed = JSON.parse(value) as unknown
    if (!isPlainObject(parsed)) {
      return null
    }
    const id = Number(parsed.id)
    const username = normalizeStoredText(parsed.username)
    const nickname = normalizeStoredText(parsed.nickname)
    const avatar = normalizeStoredText(parsed.avatar)
    const role = normalizeStoredText(parsed.role)
    if (!Number.isFinite(id) || id <= 0 || !username || !nickname || !role) {
      return null
    }
    return {
      id,
      username,
      nickname,
      avatar,
      role,
      totpEnabled: parsed.totpEnabled === true || parsed.totpEnabled === 1 || parsed.totpEnabled === '1'
    }
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    return {
      isLoggedIn: localStorage.getItem('isLoggedIn') === 'true',
      userInfo: parseStoredUserInfo(localStorage.getItem('userInfo')),
      token: localStorage.getItem('satoken') || ''
    }
  },
  actions: {
    setSession(userInfo: UserInfo, token: string) {
      this.isLoggedIn = true
      this.userInfo = userInfo
      this.token = token
      localStorage.setItem('isLoggedIn', 'true')
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
      localStorage.setItem('satoken', token)
    },
    logout() {
      this.isLoggedIn = false
      this.userInfo = null
      this.token = ''
      localStorage.removeItem('isLoggedIn')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('satoken')
    }
  }
})
