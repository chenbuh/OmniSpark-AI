import { defineStore } from 'pinia'
import { taskApi } from '@/api/tasks'
import { useAssetStore } from './asset'

export interface GenerationTask {
  id: number
  projectId: number
  providerId: number
  taskType: 'image' | 'video'
  prompt: string
  negativePrompt?: string
  status: 'pending' | 'running' | 'success' | 'failed'
  progress?: number
  progressText: string
  modelName: string
  options?: any
  errorMessage?: string
  resultAssetId?: number
  requestJson?: string
  responseJson?: string
  createdAt: string
}

export const useTaskStore = defineStore('task', {
  state: () => ({
    tasks: [] as GenerationTask[]
  }),
  actions: {
    normalizeTask(task: any): GenerationTask {
      const normalizedProgress = normalizeProgress(task.progress)
      return {
        id: Number(task.id),
        projectId: Number(task.projectId),
        providerId: Number(task.providerId),
        taskType: task.taskType,
        prompt: task.prompt || '',
        negativePrompt: task.negativePrompt || undefined,
        status: task.status,
        progress: normalizedProgress == null ? undefined : normalizedProgress,
        progressText: task.progressText || '',
        modelName: task.modelName || '',
        options: task.options,
        errorMessage: task.errorMessage || undefined,
        resultAssetId: task.resultAssetId == null ? undefined : Number(task.resultAssetId),
        requestJson: task.requestJson || undefined,
        responseJson: task.responseJson || undefined,
        createdAt: String(task.createdAt || '').replace('T', ' ').substring(0, 19)
      }
    },
    setTasks(tasks: any[]) {
      this.tasks = tasks.map(task => this.normalizeTask(task))
    },
    upsertTask(task: any) {
      const normalized = this.normalizeTask(task)
      const index = this.tasks.findIndex(item => item.id === normalized.id)
      if (index === -1) {
        this.tasks.unshift(normalized)
      } else {
        this.tasks[index] = normalized
      }
      return normalized
    },
    async refresh(params?: { projectId?: number; status?: string }) {
      const res = await taskApi.getTasks(params)
      this.setTasks(res.data || [])
      return this.tasks
    },
    getTasksByProject(projectId: number) {
      return this.tasks.filter(t => t.projectId === projectId)
    },
    async retryTask(id: number) {
      await taskApi.retryTask(id)
      await this.refresh()
      await useAssetStore().refresh()
    },
    async deleteTask(id: number) {
      await taskApi.deleteTask(id)
      this.tasks = this.tasks.filter(t => t.id !== id)
    },
    clear() {
      this.tasks = []
    }
  }
})

function normalizeProgress(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  if (Number.isNaN(parsed)) {
    return null
  }
  return Math.max(0, Math.min(100, parsed))
}
