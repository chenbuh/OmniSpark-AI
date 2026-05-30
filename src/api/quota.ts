import request from './request'

export interface QuotaSummary {
  quotaLimit: number
  quotaUsed: number
  remaining: number
}

export interface QuotaRecord {
  id: number
  userId: number
  projectId: number
  taskId?: number
  quotaType: string
  amount: number
  remark?: string
  createdAt: string
}

export const quotaApi = {
  async getSummary() {
    return request.get('/api/quota/summary')
  },

  async getRecords(projectId?: number) {
    return request.get('/api/quota/records', { params: { projectId } })
  }
}
