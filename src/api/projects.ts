import request from './request'

export interface ProjectPageResult<T> {
  total: number
  pages: number
  records: T[]
}

export const projectApi = {
  // 获取项目列表
  async getProjects() {
    return request.get('/api/projects')
  },

  async getProjectsPage(params: { page: number; pageSize: number }) {
    return request.get('/api/projects/page', { params })
  },

  // 创建项目
  async createProject(params: { name: string; description: string }) {
    return request.post('/api/projects', params)
  },

  // 编辑项目
  async updateProject(id: number, params: { name: string; description: string }) {
    return request.put(`/api/projects/${id}`, params)
  },

  // 删除项目
  async deleteProject(id: number) {
    return request.delete(`/api/projects/${id}`)
  }
}
