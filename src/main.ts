import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import App from './App.vue'
import router from './router'
import { authApi } from './api/auth'
import { useUserStore } from './store/user'
import { useTaskStore } from './store/task'
import { useProjectStore } from './store/project'
import { useModelProviderStore } from './store/provider'
import { useAssetStore } from './store/asset'
import { usePlatformStore } from './store/platform'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(naive)

const userStore = useUserStore()
const taskStore = useTaskStore()
const projectStore = useProjectStore()
const providerStore = useModelProviderStore()
const assetStore = useAssetStore()
const platformStore = usePlatformStore()
const token = localStorage.getItem('satoken')
if (token) {
  try {
    await authApi.getMe()
  } catch {
    userStore.logout()
    taskStore.clear()
    projectStore.clear()
    providerStore.clear()
    assetStore.clear()
  }
}
void platformStore.refreshProfile()

app.mount('#app')

// 本地开发环境下主动注销旧的 PWA 缓存，避免页面长期停留在历史版本
if ('serviceWorker' in navigator && ['localhost', '127.0.0.1'].includes(window.location.hostname)) {
  navigator.serviceWorker.getRegistrations().then((registrations) => {
    registrations.forEach((registration) => {
      registration.unregister().catch(() => {
        // 注销失败时静默处理
      })
    })
  })
}
