<template>
  <n-config-provider :theme="isDark ? darkTheme : null" :theme-overrides="themeOverrides">
    <n-global-style />
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <n-layout :style="layoutStyle" :native-scrollbar="false">
            <router-view />
          </n-layout>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import hljs from 'highlight.js/lib/core'
import 'highlight.js/styles/atom-one-dark.css'
import json from 'highlight.js/lib/languages/json'
hljs.registerLanguage('json', json)
// Naive UI code component will auto-detect hljs on window
;(window as any).hljs = hljs

import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import {
  darkTheme,
  NConfigProvider,
  NGlobalStyle,
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  NLayout
} from 'naive-ui'

const router = useRouter()

// ===== 主题切换 =====
const THEME_KEY = 'omnispark-theme'
const isDark = ref(localStorage.getItem(THEME_KEY) !== 'light')

const layoutStyle = computed(() => ({
  height: '100vh',
  backgroundColor: 'var(--bg-color)',
  color: 'var(--text-primary)'
}))

const themeOverrides = computed(() => ({
  common: {
    primaryColor: '#10b981',
    primaryColorHover: '#34d399',
    primaryColorPressed: '#059669',
    infoColor: '#3b82f6',
    infoColorHover: '#60a5fa',
    warningColor: '#f59e0b',
    warningColorHover: '#fbbf24',
    errorColor: '#ef4444',
    errorColorHover: '#f87171',
    bodyColor: isDark.value ? '#05070c' : '#ffffff',
    cardColor: isDark.value ? '#0f172a' : '#ffffff'
  }
}))

function toggleTheme() {
  isDark.value = !isDark.value
  localStorage.setItem(THEME_KEY, isDark.value ? 'dark' : 'light')
  document.body.className = isDark.value ? 'dark' : 'light'
  window.dispatchEvent(new CustomEvent('theme-changed', { detail: { isDark: isDark.value } }))
}

// 初始化 body class
document.body.className = isDark.value ? 'dark' : 'light'

// 暴露到 window 供其他组件调用
;(window as any).__toggleTheme = toggleTheme
;(window as any).__isDark = isDark

// 监听维护模式事件
onMounted(() => {
  window.addEventListener('system-maintenance', maintenanceHandler)
  window.addEventListener('auth-unauthorized', unauthorizedHandler)
})
onUnmounted(() => {
  window.removeEventListener('system-maintenance', maintenanceHandler)
  window.removeEventListener('auth-unauthorized', unauthorizedHandler)
})
const maintenanceHandler = (e: Event) => {
  const msg = (e as CustomEvent).detail || '系统维护中，请稍后再试'
  // 创建一个 Naive UI 通知（通过事件触发子组件）
  window.dispatchEvent(new CustomEvent('notify-maintenance', { detail: msg }))
  console.warn('[Maintenance]', msg)
}
// 401 登录失效：清空本地会话并跳转登录页
const unauthorizedHandler = () => {
  const store = useUserStore()
  store.logout()
  if (router.currentRoute.value.name !== 'Login') {
    router.replace({ name: 'Login' })
  }
}
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  overflow: hidden;
  transition: background-color 0.3s, color 0.3s;
}
body.dark { background-color: #05070c; color: #f3f4f6; }
body.light { background-color: #f5f5f5; color: #1f2937; }

/* ===== 双主题 CSS 变量 ===== */
body.dark {
  --bg-color: #05070c;
  --bg-secondary: #0b0f17;
  --card-color: #0f172a;
  --text-primary: #f3f4f6;
  --text-secondary: #e5e7eb;
  --text-muted: #9ca3af;
  --border-color: rgba(255,255,255,0.08);
  --border-light: rgba(255,255,255,0.04);
}
body.light {
  --bg-color: #f5f5f5;
  --bg-secondary: #ffffff;
  --card-color: #ffffff;
  --text-primary: #1f2937;
  --text-secondary: #374151;
  --text-muted: #6b7280;
  --border-color: rgba(0,0,0,0.08);
  --border-light: rgba(0,0,0,0.04);
}

/* 磨砂玻璃全局微调 */
.n-card.glass-card {
  backdrop-filter: blur(16px) !important;
  transition: background 0.3s, border-color 0.3s, box-shadow 0.3s;
}
body.dark .n-card.glass-card {
  background: rgba(15, 23, 42, 0.4) !important;
  border: 1px solid var(--border-color) !important;
}
body.light .n-card.glass-card {
  background: rgba(255, 255, 255, 0.7) !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04) !important;
}

/* ===== 亮色模式全局覆盖 ===== */
body.light {
  --bg-color: #f5f5f5 !important;
  --bg-secondary: #ffffff !important;
  --card-color: #ffffff !important;
  --text-primary: #1f2937 !important;
  --text-secondary: #374151 !important;
  --text-muted: #6b7280 !important;
  --border-color: rgba(0,0,0,0.08) !important;
  --border-light: rgba(0,0,0,0.04) !important;
}
body.light .n-layout-page-header { background: #ffffff !important; }
body.light .n-page-header-title { color: #1f2937 !important; }
body.light .n-data-table-th { background: #f9fafb !important; }
body.light .n-data-table-td { background: #ffffff !important; }
body.light .n-input { --n-color: #ffffff !important; --n-border: 1px solid #d1d5db !important; }
body.light .n-select .n-base-selection { --n-color: #ffffff !important; }
body.light code { color: #374151 !important; background: rgba(0,0,0,0.04) !important; padding: 1px 4px; border-radius: 3px; }
body.light h1, body.light h2, body.light h3, body.light h4 { color: #1f2937 !important; }
body.light .page-header h2 { color: #1f2937 !important; }
body.light .n-card__header { color: #1f2937 !important; }
body.light .n-tag { --n-text-color: inherit !important; }
body.light .n-data-table .n-data-table-td .n-ellipsis { color: #374151 !important; }
body.light .n-data-table-empty { color: #9ca3af !important; }
body.light .n-empty__description { color: #9ca3af !important; }
body.light .n-progress-text { color: #374151 !important; }
body.light .n-menu .n-menu-item-content .n-menu-item-content-header { color: #374151 !important; }
body.light .n-menu .n-menu-item-content.n-menu-item-content--active .n-menu-item-content-header { color: #10b981 !important; }
body.light .n-drawer-header { color: #1f2937 !important; }
body.light .n-alert-body__content { color: #374151 !important; }
body.light .n-descriptions-label { color: #6b7280 !important; }
body.light .n-descriptions-value { color: #1f2937 !important; }
body.light .n-form-item-label { color: #374151 !important; }
body.light .n-collapse-item__header { color: #374151 !important; }
body.light .n-collapse-item__content { color: #374151 !important; }
body.light .n-divider { --n-color: rgba(0,0,0,0.06) !important; }
body.light .n-switch__rail { background-color: #d1d5db !important; }
body.light .n-radio-button { color: #374151 !important; }
body.light .n-radio-button.n-radio-button--active { background: #10b981 !important; color: #fff !important; }
body.light .n-slider-rail { background: #d1d5db !important; }
body.light .n-input-number .n-input { background: #ffffff !important; }
body.light .n-select .n-base-selection-label { background: #ffffff !important; }
body.light .n-date-picker .n-input { background: #ffffff !important; }
body.light .n-card__extra { color: #374151 !important; }
body.light .n-statistic-value { color: #1f2937 !important; }
body.light .n-drawer-body { color: #374151 !important; }
body.light .n-scrollbar-content { color: #374151 !important; }

/* 全局覆盖：所有页面 glass-card 边框 */
.n-card.glass-card { border-color: var(--border-color) !important; }
.glass-card { border-color: var(--border-color) !important; }

/* 全局覆盖：通用文字颜色 */
.summary-label, .stat-label, .chart-title, .count-lbl { color: var(--text-muted) !important; }
.stats-label, .slider-lbl, .config-hint { color: var(--text-muted) !important; }
.status-value, .status-name { color: var(--text-muted) !important; }
.date-lbl, .step-lbl, .provider-lbl { color: var(--text-muted) !important; }
.post-meta, .post-author { color: var(--text-muted) !important; }
.tpl-meta, .card-meta { color: var(--text-muted) !important; }
.member-username, .team-meta { color: var(--text-muted) !important; }
.detail-section .section-title { color: var(--text-primary) !important; }

/* 表格通用样式 — 强覆盖对抗 scoped */
.admin-table { background: transparent !important; }
.admin-table th { background: rgba(128,128,128,0.03) !important; color: var(--text-muted) !important; border-bottom: 1px solid var(--border-color) !important; font-size: 12px !important; }
.admin-table td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary) !important; padding: 8px !important; font-size: 13px !important; }
.n-data-table .n-data-table-th { background: rgba(128,128,128,0.03) !important; color: var(--text-muted) !important; }
.n-data-table .n-data-table-td { border-bottom: 1px solid var(--border-light) !important; color: var(--text-secondary) !important; }

/* 卡片交互 */
.glass-card { transition: transform .2s, box-shadow .2s, border-color .2s !important; }
.glass-card:hover { transform: translateY(-2px); box-shadow: 0 8px 30px rgba(0,0,0,0.15) !important; }
body.light .glass-card:hover { box-shadow: 0 4px 20px rgba(0,0,0,0.06) !important; }

/* 弹窗和抽屉文字 */
.n-drawer-content, .n-drawer-header, .n-drawer-body { color: var(--text-primary) !important; }
.n-card__header { color: var(--text-primary) !important; }
.n-card__content { color: var(--text-secondary) !important; }
.n-modal, .n-dialog { color: var(--text-primary) !important; }

/* 全局覆盖：统一页面标题和文字颜色 */
.page-header h2 { color: var(--text-primary) !important; }
.page-header .subtitle { color: var(--text-muted) !important; margin: 0; font-size: 13px; }
.subtitle { color: var(--text-muted) !important; }
h1, h2, h3, h4, h5, h6 { color: var(--text-primary); }
.page-content-wrapper, .admin-page-content { color: var(--text-secondary); }

/* 抑制 aria-hidden 焦点警告 — Naive UI 内部行为 */
[aria-hidden="true"] { pointer-events: none; }

/* 登录页覆盖 — light mode */
body.light .login-card.glass-card { background: rgba(255,255,255,0.9) !important; }
body.light .gradient-bg .bubble { opacity: 0.15; }

/* 统一页面间距 */
.page-header { margin-bottom: 24px; display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.page-header h2 { font-size: 24px; font-weight: 700; margin: 0 0 6px 0; }

/* 空状态统一 */
.n-empty { padding: 40px 0; }
.n-empty__description { font-size: 13px !important; }

/* 滚动条优化 */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: rgba(128,128,128,0.2); border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: rgba(16,185,129,0.3); }

/* 菜单文字颜色 */
.n-menu .n-menu-item-content .n-menu-item-content-header {
  font-size: 13px !important;
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 1200px) {
  .stats-summary-grid { grid-template-columns: repeat(2, 1fr) !important; }
  .filter-tabs { max-width: 100% !important; overflow-x: auto; }
}
@media (max-width: 768px) {
  .page-header { flex-direction: column !important; }
  .stats-summary-grid { grid-template-columns: 1fr !important; }
  .posts-grid { grid-template-columns: 1fr !important; }
  .cards-grid { grid-template-columns: 1fr 1fr !important; }
  .assets-grid { grid-template-columns: repeat(2, 1fr) !important; }
}
</style>
