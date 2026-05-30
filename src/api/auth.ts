import request from './request'
import { useUserStore } from '@/store/user'
import { useTaskStore } from '@/store/task'
import { useProjectStore } from '@/store/project'
import { useModelProviderStore } from '@/store/provider'
import { useAssetStore } from '@/store/asset'

async function hydrateWorkspace() {
  await Promise.allSettled([
    useProjectStore().refresh(),
    useTaskStore().refresh(),
    useModelProviderStore().refresh(),
    useAssetStore().refresh()
  ])
}

export const authApi = {
  // 登录
  async login(params: { username: string; password?: string }) {
    const res = await request.post('/api/auth/login', params)
    useUserStore().setSession(res.data.userInfo, res.data.token)
    await hydrateWorkspace()
    return res
  },

  // 注册
  async register(params: { username: string; passwordHash: string; nickname: string }) {
    return request.post('/api/auth/register', params)
  },

  // 退出
  async logout() {
    let res
    try {
      res = await request.post('/api/auth/logout')
    } finally {
      useUserStore().logout()
      useTaskStore().clear()
      useProjectStore().clear()
      useModelProviderStore().clear()
      useAssetStore().clear()
    }
    return res
  },

  // 获取当前登录用户
  async getMe() {
    const res = await request.get('/api/auth/me')
    const token = localStorage.getItem('satoken') || ''
    useUserStore().setSession(res.data, token)
    await hydrateWorkspace()
    return res
  }
}
