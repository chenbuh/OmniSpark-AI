import request from './request'

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
  async list(projectId?: number, type?: string, sort = 'newest') {
    return request.get('/api/style-cards', { params: { projectId, type, sort } })
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
