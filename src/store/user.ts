import { defineStore } from 'pinia'

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string // 'admin' | 'user'
}

export const useUserStore = defineStore('user', {
  state: () => {
    return {
      isLoggedIn: localStorage.getItem('isLoggedIn') === 'true',
      userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null') as UserInfo | null,
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
