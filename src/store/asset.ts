import { defineStore } from 'pinia'
import { assetApi } from '@/api/assets'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function resolveBackendOrigin(baseUrl: string): string {
  try {
    return new URL(baseUrl, window.location.origin).origin
  } catch {
    return 'http://localhost:8080'
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
    return `${backendOrigin}${raw}`
  }
  return `${backendOrigin}/${raw.replace(/^\.?\//, '')}`
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
      return {
        id,
        projectId,
        taskId: asset.taskId == null ? undefined : Number(asset.taskId),
        assetType,
        fileName: typeof asset.fileName === 'string' ? asset.fileName : '',
        fileUrl: resolveAssetUrl(asset.fileUrl),
        thumbUrl: resolveAssetUrl(asset.thumbUrl || asset.fileUrl),
        mimeType: typeof asset.mimeType === 'string' ? asset.mimeType : '',
        fileSize: formatFileSize(fileSizeBytes),
        fileSizeBytes: fileSizeBytes == null ? undefined : fileSizeBytes,
        favorite: Number(asset.favorite ?? 0) !== 0,
        prompt: typeof asset.prompt === 'string' && asset.prompt ? asset.prompt : undefined,
        modelName: typeof asset.modelName === 'string' && asset.modelName ? asset.modelName : undefined,
        createdAt: String(asset.createdAt || '').replace('T', ' ').substring(0, 19) || '--'
      }
    },
    setAssets(assets: any[]) {
      this.assets = assets.map(item => this.normalizeAsset(item))
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
      const res = await assetApi.favoriteAsset(id)
      const updated = this.normalizeAsset(res.data)
      const idx = this.assets.findIndex(item => item.id === id)
      if (idx !== -1) {
        this.assets[idx] = updated
      }
      return updated
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

function isPlainObject(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
