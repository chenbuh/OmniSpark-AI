import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const REQUEST_SIGNING_SECRET = import.meta.env.VITE_REQUEST_SIGNING_SECRET || 'omnispark-dev-signing-secret-2026'

// 统一的接口返回结构，契合 docs/初始化骨架与DTO-VO设计.md 的 ApiResult
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

function shouldSignRequest(config: InternalAxiosRequestConfig): boolean {
  const method = (config.method || 'get').toLowerCase()
  if (method === 'get' || method === 'head' || method === 'options') {
    return false
  }
  const url = config.url || ''
  return url.startsWith('/api/auth/')
    || url.startsWith('/api/admin/users')
}

function isPlainObject(value: unknown): value is Record<string, any> {
  return Object.prototype.toString.call(value) === '[object Object]'
}

function getHeaderValue(headers: InternalAxiosRequestConfig['headers'], name: string): string {
  if (!headers) {
    return ''
  }
  const getter = (headers as any).get
  if (typeof getter === 'function') {
    const value = getter.call(headers, name)
    if (typeof value === 'string') {
      return value
    }
  }
  const direct = headers[name]
  if (typeof direct === 'string') {
    return direct
  }
  const lower = headers[name.toLowerCase()]
  if (typeof lower === 'string') {
    return lower
  }
  const targetName = name.toLowerCase()
  for (const [key, value] of Object.entries(headers as Record<string, unknown>)) {
    if (key.toLowerCase() === targetName && typeof value === 'string') {
      return value
    }
  }
  return ''
}

function shouldBypassCache(config: InternalAxiosRequestConfig): boolean {
  const noCache = getHeaderValue(config.headers, 'x-no-cache').toLowerCase()
  const cacheControl = getHeaderValue(config.headers, 'Cache-Control').toLowerCase()
  return noCache === '1'
    || noCache === 'true'
    || cacheControl.includes('no-cache')
    || cacheControl.includes('no-store')
}

function isFormUrlEncodedRequest(config: InternalAxiosRequestConfig): boolean {
  return getHeaderValue(config.headers, 'Content-Type').toLowerCase().startsWith('application/x-www-form-urlencoded')
}

function stableStringify(value: unknown): string {
  if (value === null || value === undefined) {
    return 'null'
  }
  if (Array.isArray(value)) {
    return `[${value.map(item => stableStringify(item)).join(',')}]`
  }
  if (isPlainObject(value)) {
    const keys = Object.keys(value).sort()
    return `{${keys.map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`
  }
  return JSON.stringify(value)
}

function normalizeFormUrlEncoded(value: string | URLSearchParams): string {
  const params = value instanceof URLSearchParams ? value : new URLSearchParams(value)
  const normalized = new URLSearchParams()
  const keys = Array.from(new Set(Array.from(params.keys()))).sort()
  keys.forEach((key) => {
    params.getAll(key).forEach((item) => {
      normalized.append(key, item)
    })
  })
  return normalized.toString()
}

function serializeRequestBody(config: InternalAxiosRequestConfig): string {
  const data = config.data
  if (data == null || data === '') {
    return ''
  }
  if (typeof data === 'string') {
    if (isFormUrlEncodedRequest(config)) {
      const serialized = normalizeFormUrlEncoded(data)
      config.data = serialized
      return serialized
    }
    return data
  }
  if (data instanceof URLSearchParams) {
    const serialized = normalizeFormUrlEncoded(data)
    config.data = serialized
    return serialized
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    throw new Error('当前敏感请求暂不支持 FormData 签名')
  }
  if (isPlainObject(data) || Array.isArray(data)) {
    const serialized = stableStringify(data)
    config.data = serialized
    return serialized
  }
  return String(data)
}

function appendParams(url: URL, params: any) {
  if (!params) {
    return
  }
  if (params instanceof URLSearchParams) {
    params.forEach((value, key) => url.searchParams.append(key, value))
    return
  }
  if (isPlainObject(params)) {
    Object.keys(params).sort().forEach((key) => {
      const value = params[key]
      if (value === undefined || value === null) {
        return
      }
      if (Array.isArray(value)) {
        value.forEach(item => url.searchParams.append(key, String(item)))
        return
      }
      url.searchParams.append(key, String(value))
    })
  }
}

function buildPathWithQuery(config: InternalAxiosRequestConfig): string {
  const target = new URL(config.url || '/', API_BASE_URL)
  appendParams(target, config.params)
  return `${target.pathname}${target.search}`
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

function arrayBufferToHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map(byte => byte.toString(16).padStart(2, '0'))
    .join('')
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(value))
  return arrayBufferToHex(digest)
}

async function hmacSha256Base64(value: string): Promise<string> {
  const key = await window.crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(REQUEST_SIGNING_SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const signature = await window.crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value))
  return arrayBufferToBase64(signature)
}

function generateNonce() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  window.crypto.getRandomValues(bytes)
  return Array.from(bytes).map(byte => byte.toString(16).padStart(2, '0')).join('')
}

const request: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('satoken')
    if (token && config.headers) {
      config.headers['satoken'] = token
    }

    // GET 请求检查缓存
    if (isCacheable(config.method) && !shouldBypassCache(config)) {
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

    if (shouldSignRequest(config)) {
      if (!window.crypto?.subtle) {
        throw new Error('当前浏览器不支持请求签名，请升级浏览器后重试')
      }
      const body = serializeRequestBody(config)
      const bodyHash = await sha256Hex(body)
      const timestamp = Date.now().toString()
      const nonce = generateNonce()
      const method = (config.method || 'post').toUpperCase()
      const pathWithQuery = buildPathWithQuery(config)
      const payload = `${method}\n${pathWithQuery}\n${bodyHash}\n${timestamp}\n${nonce}`
      const signature = await hmacSha256Base64(payload)
      if (config.headers) {
        config.headers['X-Timestamp'] = timestamp
        config.headers['X-Nonce'] = nonce
        config.headers['X-Sign'] = signature
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
    if (isCacheable(response.config.method) && !shouldBypassCache(response.config as any)) {
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
    // 429 触发限流：透传后端的具体提示文案（如"登录尝试过于频繁"）
    if (error?.response?.status === 429) {
      const msg = error?.response?.data?.message || '操作过于频繁，请稍后再试'
      return Promise.reject(new Error(msg))
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
