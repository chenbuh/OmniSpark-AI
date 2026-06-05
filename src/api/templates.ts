import request from './request'

export interface PageResult<T> {
  total: number
  pages: number
  records: T[]
}

export interface PromptTemplate {
  id: number
  projectId: number
  userId?: number
  username?: string
  nickname?: string
  avatar?: string
  name: string
  content: string
  negativePrompt?: string
  modelName?: string
  tag: string
  likesCount?: number
  commentsCount?: number
  liked?: number
  status: number
  createdAt?: string
}

export interface PromptTemplatePayload {
  projectId: number
  name: string
  content: string
  negativePrompt?: string
  modelName?: string
  tag?: string
  status?: number
}

export const templateApi = {
  // 获取提示词模板列表
  async getTemplates(params?: {
    projectId?: number
    tag?: string
    search?: string
    sort?: string
    page?: number
    pageSize?: number
  }) {
    return request.get<PageResult<PromptTemplate>>('/api/prompt-templates', {
      params: {
        projectId: params?.projectId,
        tag: params?.tag,
        search: params?.search,
        sort: params?.sort || 'newest',
        page: params?.page,
        pageSize: params?.pageSize
      }
    })
  },

  async get(id: number) {
    return request.get<PromptTemplate>(`/api/prompt-templates/${id}`)
  },

  async getTags() {
    return request.get<string[]>('/api/prompt-templates/tags')
  },

  // 新增模板
  async createTemplate(params: PromptTemplatePayload) {
    return request.post('/api/prompt-templates', params)
  },

  // 更新模板
  async update(id: number, params: Partial<PromptTemplatePayload>) {
    return request.put(`/api/prompt-templates/${id}`, params)
  },

  // 删除模板
  async deleteTemplate(id: number) {
    return request.delete(`/api/prompt-templates/${id}`)
  }
}
