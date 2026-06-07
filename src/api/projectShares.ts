import request from './request'
import { collectAllPageRecords } from './pagination'

export interface ProjectShare {
  id: number
  projectId: number
  projectName?: string
  teamId: number
  teamName?: string
  permission: string
  createdAt: string
}

export interface ProjectSharePageResult<T> {
  total: number
  pages: number
  records: T[]
}

export const projectShareApi = {
  async getShares(projectId: number) {
    return request.get(`/api/project-shares/${projectId}`)
  },

  async getAllShares(projectId: number) {
    return collectAllPageRecords<ProjectShare>({
      loadPage: (page, pageSize) => projectShareApi.getSharesPage(projectId, { page, pageSize }),
      errorMessage: '共享列表待确认'
    })
  },

  async getSharesPage(projectId: number, params: { page: number; pageSize: number }) {
    return request.get(`/api/project-shares/${projectId}/page`, { params })
  },

  async createShare(params: { projectId: number; teamId: number; permission: string }) {
    return request.post('/api/project-shares', params)
  },

  async updatePermission(shareId: number, permission: string) {
    return request.put(`/api/project-shares/${shareId}/permission`, null, { params: { permission } })
  },

  async removeShare(shareId: number) {
    return request.delete(`/api/project-shares/${shareId}`)
  }
}
