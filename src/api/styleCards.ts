import request from './request'

export interface PageResult<T> {
  total: number
  pages: number
  records: T[]
}

export interface StyleCard {
  id: number
  projectId: number
  userId?: number
  username?: string
  nickname?: string
  avatar?: string
  name: string
  type: 'character' | 'style'
  content: string
  negativePrompt?: string
  modelName?: string
  providerId?: number
  refAssetId?: number
  cfg?: number
  steps?: number
  size?: string
  paramsJson?: string
  previewUrl?: string
  tag?: string
  likesCount?: number
  commentsCount?: number
  liked?: number
  status: number
  createdAt: string
}

export const styleCardApi = {
  async list(params?: {
    projectId?: number
    type?: string
    search?: string
    sort?: string
    page?: number
    pageSize?: number
  }) {
    return request.get<PageResult<StyleCard>>('/api/style-cards', {
      params: {
        projectId: params?.projectId,
        type: params?.type,
        search: params?.search,
        sort: params?.sort || 'newest',
        page: params?.page,
        pageSize: params?.pageSize
      }
    })
  },

  async get(id: number) {
    return request.get(`/api/style-cards/${id}`)
  },

  async create(params: Omit<StyleCard, 'id' | 'status' | 'createdAt'>) {
    return request.post('/api/style-cards', { ...params, status: 1 })
  },

  async update(id: number, params: Partial<StyleCard>) {
    return request.put(`/api/style-cards/${id}`, params)
  },

  async delete(id: number) {
    return request.delete(`/api/style-cards/${id}`)
  }
}
