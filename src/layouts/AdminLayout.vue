<template>
  <n-layout has-sider class="admin-layout">
    <!-- 左侧侧边栏 -->
    <n-layout-sider
      bordered collapse-mode="width" :collapsed-width="64" :width="220"
      show-trigger="arrow-circle"
      content-style="display: flex; flex-direction: column; height: 100%;"
      :native-scrollbar="false" class="admin-sider"
    >
      <div class="admin-logo"><span class="admin-logo-text">⚙ {{ platformName }} 管理系统</span></div>
      <div class="admin-menu-box">
        <n-menu :collapsed="collapsed" :collapsed-width="64" :collapsed-icon-size="22"
          :options="menuOptions" :value="activeKey" @update:value="handleMenuSelect" />
      </div>
      <div class="admin-footer">
        <n-button size="small" quaternary @click="router.push('/dashboard')">← 返回主站</n-button>
      </div>
    </n-layout-sider>

    <!-- 右侧内容 -->
    <n-layout class="admin-content">
      <n-layout-header class="admin-header">
        <div class="header-left"><span class="header-title">{{ pageTitle }}</span></div>
        <div class="header-right">
          <n-button size="small" quaternary @click="toggleTheme">
            {{ isDark ? '☀️ 亮色' : '🌙 暗黑' }}
          </n-button>
          <span class="header-user">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '未登录用户' }}</span>
          <n-button size="small" quaternary @click="handleLogout">退出</n-button>
        </div>
      </n-layout-header>
      <n-layout-content class="admin-page-content"><router-view /></n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup lang="ts">
import { ref, computed, h, type Component, type Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NLayout, NLayoutSider, NLayoutHeader, NLayoutContent, NMenu, NButton, NIcon, type MenuOption } from 'naive-ui'
import { usePlatformStore } from '@/store/platform'
import { useUserStore } from '@/store/user'
import {
  BarChart3, ClipboardList, Library, Shield, Settings,
  Activity, Megaphone, Trash2, BookOpen, FolderOpen
} from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const platformStore = usePlatformStore()
const userStore = useUserStore()
const collapsed = ref(false)
const platformName = computed(() => platformStore.platformName || 'OmniSpark AI')

type ThemeWindow = Window & {
  __toggleTheme?: () => void
  __isDark?: Ref<boolean>
}

const getThemeWindow = (): ThemeWindow => window as ThemeWindow

// 从 App.vue 读取主题状态
const isDark = computed(() => getThemeWindow().__isDark?.value !== false)
const toggleTheme = () => getThemeWindow().__toggleTheme?.()

const renderIcon = (icon: Component) => () => h(NIcon, null, () => h(icon))

const menuOptions: MenuOption[] = [
  { label: '控制台', key: 'admin-dashboard', icon: renderIcon(BarChart3) },
  { label: '任务监管', key: 'admin-tasks', icon: renderIcon(ClipboardList) },
  { label: '资产监管', key: 'admin-assets', icon: renderIcon(Library) },
  { label: '用户管理', key: 'admin-users', icon: renderIcon(Shield) },
  { label: '性能监控', key: 'admin-monitor', icon: renderIcon(Activity) },
  { label: '系统更新', key: 'admin-update', icon: renderIcon(Activity) },
  { label: '数据字典', key: 'admin-dict', icon: renderIcon(BookOpen) },
  { label: '文件管理', key: 'admin-files', icon: renderIcon(FolderOpen) },
  { label: '定时任务', key: 'admin-scheduled-tasks', icon: renderIcon(Activity) },
  { label: 'Webhook', key: 'admin-webhooks', icon: renderIcon(Activity) },
  { label: '系统日志', key: 'admin-logs', icon: renderIcon(Activity) },
  { label: '访问日志', key: 'admin-access-logs', icon: renderIcon(Activity) },
  { label: '维护模式', key: 'admin-maintenance', icon: renderIcon(Shield) },
  { label: '系统公告', key: 'admin-announcements', icon: renderIcon(Megaphone) },
  { label: '数据清理', key: 'admin-cleanup', icon: renderIcon(Trash2) },
  { label: '登录日志', key: 'admin-login-logs', icon: renderIcon(Activity) },
  { label: '系统配置', key: 'admin-config', icon: renderIcon(Settings) },
]

const activeKey = computed(() => {
  const p = route.path
  if (p.startsWith('/admin/dashboard')) return 'admin-dashboard'
  if (p.startsWith('/admin/tasks')) return 'admin-tasks'
  if (p.startsWith('/admin/assets')) return 'admin-assets'
  if (p.startsWith('/admin/users')) return 'admin-users'
  if (p.startsWith('/admin/monitor')) return 'admin-monitor'
  if (p.startsWith('/admin/update')) return 'admin-update'
  if (p.startsWith('/admin/dict')) return 'admin-dict'
  if (p.startsWith('/admin/files')) return 'admin-files'
  if (p.startsWith('/admin/scheduled-tasks')) return 'admin-scheduled-tasks'
  if (p.startsWith('/admin/webhooks')) return 'admin-webhooks'
  if (p.startsWith('/admin/logs')) return 'admin-logs'
  if (p.startsWith('/admin/access-logs')) return 'admin-access-logs'
  if (p.startsWith('/admin/maintenance')) return 'admin-maintenance'
  if (p.startsWith('/admin/announcements')) return 'admin-announcements'
  if (p.startsWith('/admin/cleanup')) return 'admin-cleanup'
  if (p.startsWith('/admin/login-logs')) return 'admin-login-logs'
  if (p.startsWith('/admin/config')) return 'admin-config'
  return 'admin-dashboard'
})

const pageTitle = computed(() => (route.meta?.title as string) || '管理系统')

const routeMap: Record<string, string> = {
  'admin-dashboard': '/admin/dashboard', 'admin-tasks': '/admin/tasks',
  'admin-assets': '/admin/assets', 'admin-users': '/admin/users',
  'admin-monitor': '/admin/monitor',
  'admin-update': '/admin/update', 'admin-dict': '/admin/dict',
  'admin-files': '/admin/files', 'admin-scheduled-tasks': '/admin/scheduled-tasks',
  'admin-webhooks': '/admin/webhooks', 'admin-logs': '/admin/logs',
  'admin-access-logs': '/admin/access-logs',
  'admin-maintenance': '/admin/maintenance', 'admin-announcements': '/admin/announcements',
  'admin-cleanup': '/admin/cleanup', 'admin-login-logs': '/admin/login-logs',
  'admin-config': '/admin/config'
}
const handleMenuSelect = (key: string) => router.push(routeMap[key] || '/admin/dashboard')

async function handleLogout() {
  try { await (await import('@/api/auth')).authApi.logout() } catch {}
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-layout { height: 100vh; background-color: var(--bg-secondary); color: var(--text-primary); overflow: hidden; }
.admin-sider { background: var(--card-color) !important; backdrop-filter: blur(16px); border-right: 1px solid var(--border-color) !important; }
.admin-logo { padding: 20px 16px; border-bottom: 1px solid var(--border-color); }
.admin-logo-text { font-size: 16px; font-weight: 700; background: linear-gradient(to right, #10b981, #3b82f6); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
.admin-menu-box { flex: 1; padding: 8px 0; overflow-y: auto; }
.admin-footer { padding: 12px 16px; border-top: 1px solid var(--border-color); }
.admin-content { display: flex; flex-direction: column; min-width: 0; min-height: 0; background-color: var(--bg-secondary) !important; }
.admin-header { display: flex; align-items: center; justify-content: space-between; height: 56px; padding: 0 24px; background: var(--card-color) !important; border-bottom: 1px solid var(--border-color) !important; }
.header-title { font-size: 15px; font-weight: 600; color: var(--text-secondary); }
.header-right { display: flex; align-items: center; gap: 16px; }
.header-user { font-size: 13px; color: var(--text-muted); }
.admin-page-content { flex: 1; min-height: 0; padding: 24px; overflow-y: auto; }
</style>
