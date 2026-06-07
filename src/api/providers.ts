import request from './request'
import { collectAllPageRecords } from './pagination'
import type { ModelProvider } from '@/store/provider'

export interface ProviderMetaOption {
  label: string
  value: string
  shortLabel?: string
  tagType?: string
}

export interface ProviderMetaVO {
  providerTypes: ProviderMetaOption[]
  audioResponseFormats: ProviderMetaOption[]
  defaults?: {
    providerType?: string
    audioResponseFormat?: string
  }
}

export interface ProviderPageResult<T> {
  total: number
  pages: number
  records: T[]
}

export const providerApi = {
  async getMeta() {
    return request.get('/api/model-providers/meta')
  },

  // 获取模型提供商列表
  async getProviders(projectId?: number) {
    return request.get('/api/model-providers', { params: { projectId } })
  },

  async getAllProviders(projectId?: number) {
    return collectAllPageRecords<ModelProvider>({
      loadPage: (page, pageSize) => providerApi.getProvidersPage({ projectId, page, pageSize }),
      errorMessage: '模型提供商数据待确认'
    })
  },

  async getProvidersPage(params: { projectId?: number; page: number; pageSize: number }) {
    return request.get('/api/model-providers/page', { params })
  },

  // 新增模型提供商
  async createProvider(params: Omit<ModelProvider, 'id'>) {
    return request.post('/api/model-providers', params)
  },

  // 修改模型提供商
  async updateProvider(id: number, params: Partial<ModelProvider>) {
    return request.put(`/api/model-providers/${id}`, params)
  },

  // 删除模型提供商
  async deleteProvider(id: number) {
    return request.delete(`/api/model-providers/${id}`)
  },

  // 测试模型提供商连接
  async testConnection(id: number) {
    return request.post(`/api/model-providers/${id}/test`)
  }
}
