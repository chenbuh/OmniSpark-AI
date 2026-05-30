import request from './request'

export interface ProjectShare {
  id: number
  projectId: number
  projectName?: string
  teamId: number
  teamName?: string
  permission: string
  createdAt: string
}

export const projectShareApi = {
  async getShares(projectId: number) {
    return request.get(`/api/project-shares/${projectId}`)
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
