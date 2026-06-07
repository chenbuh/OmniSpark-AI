import request from './request'
import { collectAllPageRecords } from './pagination'

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

  async getAllProjects() {
    return collectAllPageRecords({
      loadPage: (page, pageSize) => projectApi.getProjectsPage({ page, pageSize }),
      errorMessage: '项目数据待确认'
    })
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
