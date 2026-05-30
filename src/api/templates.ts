import request from './request'

export interface PromptTemplate {
  id: number
  projectId: number
  name: string
  content: string
  negativePrompt?: string
  modelName?: string
  tag: string
  status: number
}

export const templateApi = {
  // 获取提示词模板列表
  async getTemplates(projectId?: number) {
    return request.get('/api/prompt-templates', { params: { projectId } })
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
