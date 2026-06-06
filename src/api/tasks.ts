import request from './request'
import { collectAllPageRecords } from './pagination'

export const taskApi = {
  // 获取任务列表，支持按项目空间和状态过滤
  async getTasks(params?: { projectId?: number; status?: string }) {
    return request.get('/api/tasks', {
      params,
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async pageTasks(params?: { projectId?: number; status?: string; page?: number; pageSize?: number }) {
    return request.get('/api/tasks/page', {
      params,
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  async getAllTasks(params?: { projectId?: number; status?: string }) {
    return collectAllPageRecords({
      loadPage: (page, pageSize) => taskApi.pageTasks({
        ...params,
        page,
        pageSize
      }),
      errorMessage: '任务数据待确认'
    })
  },

  // 获取单个任务详情
  async getTask(id: number) {
    return request.get(`/api/tasks/${id}`, {
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  // 重试任务
  async retryTask(id: number) {
    return request.post(`/api/tasks/${id}/retry`)
  },

  // 删除任务
  async deleteTask(id: number) {
    return request.delete(`/api/tasks/${id}`)
  }
}
