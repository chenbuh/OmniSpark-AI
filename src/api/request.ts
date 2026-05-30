import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'

// 统一的接口返回结构，契合 docs/init-skeletons-dto-vo.md 的 ApiResult
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

// ===== SWR 内存缓存 =====
interface CacheEntry {
  data: any
  timestamp: number
}
const cacheStore = new Map<string, CacheEntry>()
const DEFAULT_TTL = 15_000 // 15 秒

function getCacheKey(config: InternalAxiosRequestConfig): string {
  const method = config.method || 'get'
  const url = config.url || ''
  const params = JSON.stringify(config.params || {})
  const data = JSON.stringify(config.data || {})
  return `${method}:${url}:${params}:${data}`
}

// 只缓存 GET 请求
function isCacheable(method?: string): boolean {
  return !method || method.toLowerCase() === 'get'
}

// 从写操作 URL 推导受影响的资源集合前缀，如 /api/assets/123/favorite -> /api/assets
function resourcePrefix(url?: string): string | null {
  if (!url) return null
  const match = url.match(/\/api\/[^/?]+/)
  return match ? match[0] : null
}

// 写操作后按集合前缀精准失效缓存，而非清空整表
function invalidateByWrite(url?: string) {
  const prefix = resourcePrefix(url)
  if (!prefix) {
    cacheStore.clear()
    return
  }
  for (const key of cacheStore.keys()) {
    if (key.includes(prefix)) {
      cacheStore.delete(key)
    }
  }
}

function looksLikeHtml(value: string): boolean {
  const normalized = value.trim().toLowerCase()
  return normalized.startsWith('<!doctype html')
    || normalized.startsWith('<html')
    || normalized.startsWith('<body')
    || normalized.includes('<head>')
    || normalized.includes('<title>')
}

function abbreviateText(value: string, max = 180): string {
  const compact = value.replace(/\s+/g, ' ').trim()
  if (compact.length <= max) {
    return compact
  }
  return `${compact.slice(0, max)}...`
}

function extractErrorMessage(error: any): string {
  const responseData = error?.response?.data
  if (typeof responseData?.message === 'string' && responseData.message.trim()) {
    return responseData.message.trim()
  }
  if (typeof responseData === 'string' && responseData.trim()) {
    if (looksLikeHtml(responseData)) {
      if (responseData.toLowerCase().includes('<title>nl-api</title>')) {
        return '接口返回了 nl-api 管理页 HTML，请确认 Base URL 填写的是 OpenAI 兼容 API 地址'
      }
      return '接口返回了 HTML 页面，请确认当前请求目标是 API 接口而不是网站首页'
    }
    return abbreviateText(responseData)
  }
  if (typeof error?.message === 'string' && error.message.trim()) {
    return error.message.trim()
  }
  return '请求失败，请稍后重试'
}

const request: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('satoken')
    if (token && config.headers) {
      config.headers['satoken'] = token
    }

    // GET 请求检查缓存
    if (isCacheable(config.method) && !config.headers?.['x-no-cache']) {
      const key = getCacheKey(config)
      const cached = cacheStore.get(key)
      if (cached && Date.now() - cached.timestamp < DEFAULT_TTL) {
        return Promise.reject({
          __fromCache: true,
          data: cached.data,
          config
        })
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200 && res.code !== 0) {
      return Promise.reject(new Error(res.message || 'Error'))
    }

    // 缓存 GET 成功响应
    if (isCacheable(response.config.method) && !response.config.headers?.['x-no-cache']) {
      const key = getCacheKey(response.config as any)
      cacheStore.set(key, { data: res, timestamp: Date.now() })
    } else if (!isCacheable(response.config.method)) {
      invalidateByWrite(response.config.url)
    }

    return res
  },
  (error) => {
    // 命中缓存的请求直接返回缓存数据
    if (error?.__fromCache) {
      return Promise.resolve(error.data)
    }
    // 401 登录失效：清除会话并跳转登录页（由 App.vue 监听处理，避免循环依赖）
    if (error?.response?.status === 401) {
      localStorage.removeItem('isLoggedIn')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('satoken')
      window.dispatchEvent(new CustomEvent('auth-unauthorized'))
    }
    // 503 维护模式处理
    if (error?.response?.status === 503) {
      const msg = error?.response?.data?.message || '系统维护中，请稍后再试'
      window.dispatchEvent(new CustomEvent('system-maintenance', { detail: msg }))
    }
    return Promise.reject(new Error(extractErrorMessage(error)))
  }
)

/** 强制清除指定 URL 的缓存 */
export function clearCache(urlPattern?: string) {
  if (!urlPattern) {
    cacheStore.clear()
    return
  }
  for (const key of cacheStore.keys()) {
    if (key.includes(urlPattern)) {
      cacheStore.delete(key)
    }
  }
}

export default request
