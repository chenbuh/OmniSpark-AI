import request from './request'

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

export const templateApi = {
  // 获取提示词模板列表
  async getTemplates(projectId?: number, sort = 'newest') {
    return request.get('/api/prompt-templates', { params: { projectId, sort } })
  },

  // 新增模板
  async createTemplate(params: Omit<PromptTemplate, 'id' | 'status'>) {
    return request.post('/api/prompt-templates', params)
  },

  // 更新模板
  async update(id: number, params: Partial<PromptTemplate>) {
    return request.put(`/api/prompt-templates/${id}`, params)
  },

  // 删除模板
  async deleteTemplate(id: number) {
    return request.delete(`/api/prompt-templates/${id}`)
  }
}
