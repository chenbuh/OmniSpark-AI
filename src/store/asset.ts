import { defineStore } from 'pinia'
import { assetApi } from '@/api/assets'
import { API_BASE_URL } from '@/api/request'

function resolveBackendOrigin(baseUrl: string): string {
  const browserOrigin = typeof window !== 'undefined' ? window.location.origin : ''
  try {
    return browserOrigin ? new URL(baseUrl, browserOrigin).origin : new URL(baseUrl).origin
  } catch {
    return browserOrigin
  }
}

export function resolveAssetUrl(url?: string | null): string {
  const raw = String(url || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw) || raw.startsWith('blob:') || raw.startsWith('data:')) {
    return raw
  }

  const backendOrigin = resolveBackendOrigin(API_BASE_URL)
  if (raw.startsWith('/')) {
    return backendOrigin ? `${backendOrigin}${raw}` : raw
  }
  const normalizedPath = raw.replace(/^\.?\//, '')
  return backendOrigin ? `${backendOrigin}/${normalizedPath}` : `/${normalizedPath}`
}

function formatFileSize(bytes?: number | null): string {
  if (!Number.isFinite(bytes) || !bytes || bytes <= 0) return '--'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes >= 100 * 1024 ? 0 : 1)} KB`
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(bytes >= 10 * 1024 * 1024 ? 1 : 2)} MB`
  }
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export interface Asset {
  id: number
  projectId: number
  taskId?: number
  assetType: 'image' | 'video' | 'reference'
  fileName: string
  fileUrl: string
  thumbUrl: string
  mimeType: string
  fileSize: string
  fileSizeBytes?: number
  favorite: boolean
  prompt?: string
  modelName?: string
  createdAt: string
}

export const useAssetStore = defineStore('asset', {
  state: () => ({
    assets: [] as Asset[]
  }),
  actions: {
    normalizeAsset(asset: unknown): Asset {
      if (!isPlainObject(asset)) {
        throw new Error('资产结果待确认')
      }
      const assetType = typeof asset.assetType === 'string' ? asset.assetType : ''
      if (assetType !== 'image' && assetType !== 'video' && assetType !== 'reference') {
        throw new Error('资产结果待确认')
      }
      const id = parseRequiredNumber(asset.id)
      const projectId = parseRequiredNumber(asset.projectId)
      const fileSizeBytes = parseOptionalFileSize(asset.fileSize)
      const taskId = asset.taskId == null ? undefined : parseOptionalPositiveNumber(asset.taskId)
      const fileName = typeof asset.fileName === 'string' ? asset.fileName.trim() : ''
      const fileUrl = resolveAssetUrl(asset.fileUrl)
      const thumbUrl = resolveAssetUrl(asset.thumbUrl || asset.fileUrl)
      const createdAt = String(asset.createdAt || '').replace('T', ' ').substring(0, 19)
      if (id <= 0 || projectId <= 0 || !fileName || !fileUrl || !thumbUrl || !createdAt || createdAt === '--') {
        throw new Error('资产结果待确认')
      }
      return {
        id,
        projectId,
        taskId,
        assetType,
        fileName,
        fileUrl,
        thumbUrl,
        mimeType: typeof asset.mimeType === 'string' ? asset.mimeType : '',
        fileSize: formatFileSize(fileSizeBytes),
        fileSizeBytes: fileSizeBytes == null ? undefined : fileSizeBytes,
        favorite: Number(asset.favorite ?? 0) !== 0,
        prompt: typeof asset.prompt === 'string' && asset.prompt ? asset.prompt : undefined,
        modelName: typeof asset.modelName === 'string' && asset.modelName ? asset.modelName : undefined,
        createdAt
      }
    },
    setAssets(assets: any[]) {
      this.assets = normalizeAssetList(assets, this.normalizeAsset)
    },
    async refresh(params?: { projectId?: number; assetType?: string; taskId?: number; limit?: number }) {
      const res = await assetApi.getAssets(params)
      if (!Array.isArray(res.data)) {
        throw new Error('资产数据待确认')
      }
      this.setAssets(res.data)
      return this.assets
    },
    getAssetsByProject(projectId: number) {
      return this.assets.filter(a => a.projectId === projectId)
    },
    async toggleFavorite(id: number) {
      const existing = this.assets.find(asset => asset.id === id)
      const previousFavorite = existing?.favorite
      const res = await assetApi.favoriteAsset(id)
      const updated = this.normalizeAsset(res.data)
      if (existing?.projectId) {
        await this.refresh({ projectId: existing.projectId })
      }
      const confirmedFromList = this.assets.find(asset => asset.id === id) || null
      const confirmed = confirmedFromList || this.normalizeAsset((await assetApi.getAsset(id)).data)
      if (confirmed.favorite !== updated.favorite) {
        throw new Error('资产状态待确认')
      }
      if (typeof previousFavorite === 'boolean' && confirmed.favorite === previousFavorite) {
        throw new Error('资产状态待确认')
      }
      const idx = this.assets.findIndex(item => item.id === id)
      if (idx !== -1) {
        this.assets[idx] = confirmed
      } else {
        this.assets.unshift(confirmed)
      }
      return confirmed
    },
    async deleteAsset(id: number) {
      const existing = this.assets.find(asset => asset.id === id)
      await assetApi.deleteAsset(id)
      if (existing?.projectId) {
        await this.refresh({ projectId: existing.projectId })
      } else {
        await this.refresh()
      }
      if (this.assets.some(asset => asset.id === id)) {
        throw new Error('资产删除结果待确认')
      }
    },
    clear() {
      this.assets = []
    }
  }
})

function parseOptionalFileSize(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function parseRequiredNumber(value: unknown): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('资产结果待确认')
  }
  return parsed
}

function parseOptionalPositiveNumber(value: unknown): number | undefined {
  if (value == null || value === '') {
    return undefined
  }
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new Error('资产结果待确认')
  }
  return parsed
}

function normalizeAssetList(assets: unknown[], normalizeAsset: (asset: unknown) => Asset) {
  const normalized = assets.map(item => normalizeAsset(item))
  const ids = new Set<number>()
  normalized.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('资产数据待确认')
    }
    ids.add(item.id)
  })
  return normalized
}

function isPlainObject(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
