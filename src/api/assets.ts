import request from './request'
import { collectAllPageRecords } from './pagination'

export interface PageResult<T> {
  total: number
  pages: number
  records: T[]
}

export interface AssetPageParams {
  scope?: 'own' | 'shared'
  projectId?: number
  assetType?: string
  taskId?: number
  favorite?: boolean
  search?: string
  sort?: string
  page?: number
  pageSize?: number
}

export interface AssetStats {
  scope?: string
  message?: string
  projectCount?: number
  total: number
  imageCount: number
  videoCount: number
  referenceCount: number
  favoriteCount: number
}

export interface AssetVersionResult {
  id: number
  taskId?: number
  assetType?: string
  fileName?: string
  fileUrl?: string
  thumbUrl?: string
  mimeType?: string
  fileSize?: number | string
  favorite?: boolean | number | string
  prompt?: string
  modelName?: string
  createdAt?: string
  [key: string]: unknown
}

export const assetApi = {
  // 获取资产列表，支持按项目 ID 和资产类型过滤
  async getAssets(params?: { projectId?: number; assetType?: string; taskId?: number; limit?: number }) {
    return request.get('/api/assets', {
      params: {
        ...params,
        limit: params?.limit ?? 100
      },
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async pageAssets(params?: AssetPageParams) {
    return request.get<PageResult<AssetVersionResult>>('/api/assets/page', {
      params: {
        scope: params?.scope || 'own',
        projectId: params?.projectId,
        assetType: params?.assetType,
        taskId: params?.taskId,
        favorite: params?.favorite,
        search: params?.search,
        sort: params?.sort || 'latest',
        page: params?.page,
        pageSize: params?.pageSize
      },
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async getAllAssets(params?: AssetPageParams) {
    return collectAllPageRecords<AssetVersionResult>({
      loadPage: (page, pageSize) => assetApi.pageAssets({
        ...params,
        page,
        pageSize
      }),
      errorMessage: '资产数据待确认'
    })
  },

  async getStats(params?: { scope?: 'own' | 'shared'; projectId?: number }) {
    return request.get<AssetStats>('/api/assets/stats', {
      params: {
        scope: params?.scope || 'own',
        projectId: params?.projectId
      },
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async getAsset(id: number) {
    return request.get(`/api/assets/${id}`, {
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async getVersions(id: number, limit = 12) {
    return request.get<AssetVersionResult[]>(`/api/assets/${id}/versions`, {
      params: { limit },
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  // 上传参考图或新资产
  async uploadAsset(formData: FormData) {
    return request.post('/api/assets/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 删除资产
  async deleteAsset(id: number) {
    return request.delete(`/api/assets/${id}`)
  },

  // 收藏/取消收藏
  async favoriteAsset(id: number) {
    return request.post(`/api/assets/${id}/favorite`)
  }
}
