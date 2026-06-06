import { defineStore } from 'pinia'
import request from '@/api/request'

const DEFAULT_PLATFORM_NAME = 'OmniSpark AI'

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function normalizePlatformName(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function requirePlatformProfile(payload: unknown) {
  if (!isPlainObject(payload)) {
    throw new Error('平台资料待确认')
  }
  const platformName = normalizePlatformName(payload.platformName)
  if (!platformName) {
    throw new Error('平台资料待确认')
  }
  return { platformName }
}

export const usePlatformStore = defineStore('platform', {
  state: () => ({
    platformName: DEFAULT_PLATFORM_NAME,
    loaded: false,
    loading: false
  }),
  actions: {
    setPlatformName(platformName: string) {
      const normalized = normalizePlatformName(platformName)
      if (!normalized) {
        return
      }
      this.platformName = normalized
      this.loaded = true
    },
    async refreshProfile() {
      if (this.loading) {
        return
      }
      this.loading = true
      try {
        const response = await request.get<unknown>('/api/system/profile')
        const profile = requirePlatformProfile(getResponseData(response, '平台资料待确认'))
        this.platformName = profile.platformName
        this.loaded = true
      } catch {
        if (!this.platformName) {
          this.platformName = DEFAULT_PLATFORM_NAME
        }
      } finally {
        this.loading = false
      }
    }
  }
})
