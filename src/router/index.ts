import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/store/user'

const adminMeta = (title: string) => ({ title, requiresAdmin: true })

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '平台登录 - 统一创作空间' }
  },
  // ===== 主站布局 =====
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: '控制台 - 统一创作空间' } },
      { path: 'generate/image', name: 'GenerateImage', component: () => import('@/views/GenerateImage.vue'), meta: { title: '生图中心 - 统一创作空间' } },
      { path: 'generate/video', name: 'GenerateVideo', component: () => import('@/views/GenerateVideo.vue'), meta: { title: '生视频中心 - 统一创作空间' } },
      { path: 'tasks', name: 'Tasks', component: () => import('@/views/Tasks.vue'), meta: { title: '任务中心 - 统一创作空间' } },
      { path: 'assets', name: 'Assets', component: () => import('@/views/Assets.vue'), meta: { title: '资产库 - 统一创作空间' } },
      { path: 'model-providers', name: 'ModelProviders', component: () => import('@/views/ModelProviders.vue'), meta: { title: '模型配置 - 统一创作空间' } },
      { path: 'prompt-templates', name: 'PromptTemplates', component: () => import('@/views/PromptTemplates.vue'), meta: { title: '提示词模板 - 统一创作空间' } },
      { path: 'stats', name: 'Stats', component: () => import('@/views/Stats.vue'), meta: { title: '用量统计 - 统一创作空间' } },
      { path: 'community', name: 'Community', component: () => import('@/views/Community.vue'), meta: { title: '社区共享 - 统一创作空间' } },
      { path: 'audit-logs', name: 'AuditLog', component: () => import('@/views/AuditLog.vue'), meta: { title: '审计日志 - 统一创作空间' } },
      { path: 'workflows', name: 'Workflows', component: () => import('@/views/Workflows.vue'), meta: { title: '工作流编排 - 统一创作空间' } },
      { path: 'style-cards', name: 'StyleCards', component: () => import('@/views/StyleCards.vue'), meta: { title: '角色/风格卡 - 统一创作空间' } },
      { path: 'teams', name: 'Teams', component: () => import('@/views/Teams.vue'), meta: { title: '团队管理 - 统一创作空间' } },
      { path: 'settings', name: 'Settings', component: () => import('@/views/Settings.vue'), meta: { title: '系统设置 - 统一创作空间' } },
    ]
  },
  // ===== 管理后台布局 =====
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboard.vue'), meta: adminMeta('管理控制台') },
      { path: 'tasks', name: 'AdminTasks', component: () => import('@/views/admin/AdminTasks.vue'), meta: adminMeta('任务监管') },
      { path: 'assets', name: 'AdminAssets', component: () => import('@/views/admin/AdminAssets.vue'), meta: adminMeta('资产监管') },
      { path: 'users', name: 'UserManagement', component: () => import('@/views/admin/UserManagement.vue'), meta: adminMeta('用户管理') },
      { path: 'monitor', name: 'AdminMonitor', component: () => import('@/views/admin/AdminMonitor.vue'), meta: adminMeta('性能监控') },
      { path: 'update', name: 'AdminUpdateCheck', component: () => import('@/views/admin/AdminUpdateCheck.vue'), meta: adminMeta('系统更新') },
      { path: 'dict', name: 'AdminDataDict', component: () => import('@/views/admin/AdminDataDict.vue'), meta: adminMeta('数据字典') },
      { path: 'files', name: 'AdminFileManager', component: () => import('@/views/admin/AdminFileManager.vue'), meta: adminMeta('文件管理') },
      { path: 'scheduled-tasks', name: 'AdminScheduledTasks', component: () => import('@/views/admin/AdminScheduledTasks.vue'), meta: adminMeta('定时任务') },
      { path: 'webhooks', name: 'AdminWebhooks', component: () => import('@/views/admin/AdminWebhooks.vue'), meta: adminMeta('Webhook') },
      { path: 'logs', name: 'AdminLogViewer', component: () => import('@/views/admin/AdminLogViewer.vue'), meta: adminMeta('系统日志') },
      { path: 'access-logs', name: 'AdminAccessLogs', component: () => import('@/views/admin/AdminAccessLogs.vue'), meta: adminMeta('访问日志') },
      { path: 'maintenance', name: 'AdminMaintenance', component: () => import('@/views/admin/AdminMaintenance.vue'), meta: adminMeta('维护模式') },
      { path: 'announcements', name: 'AdminAnnouncements', component: () => import('@/views/admin/AdminAnnouncements.vue'), meta: adminMeta('系统公告') },
      { path: 'cleanup', name: 'AdminCleanup', component: () => import('@/views/admin/AdminCleanup.vue'), meta: adminMeta('数据清理') },
      { path: 'login-logs', name: 'AdminLoginLogs', component: () => import('@/views/admin/AdminLoginLogs.vue'), meta: adminMeta('登录日志') },
      { path: 'config', name: 'SystemConfig', component: () => import('@/views/admin/SystemConfig.vue'), meta: adminMeta('系统配置') },
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.title) document.title = to.meta.title as string
  if (to.name !== 'Login' && !store.isLoggedIn) return { name: 'Login' }
  if (to.meta.requiresAdmin && store.userInfo?.role !== 'admin') return { name: 'Dashboard' }
  if (to.name === 'Login' && store.isLoggedIn) return { name: 'Dashboard' }
  return true
})

export default router
