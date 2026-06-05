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
  finishedAt?: string
}

export const useTaskStore = defineStore('task', {
  state: () => ({
    tasks: [] as GenerationTask[]
  }),
  actions: {
    normalizeTask(task: unknown): GenerationTask {
      return normalizeTaskPayload(task)
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
      if (!Array.isArray(res.data)) {
        throw new Error('任务数据待确认')
      }
      this.setTasks(res.data)
      return this.tasks
    },
    getTasksByProject(projectId: number) {
      return this.tasks.filter(t => t.projectId === projectId)
    },
    async retryTask(id: number) {
      const res = await taskApi.retryTask(id)
      const retried = this.upsertTask((res as any).data)
      await this.refresh({ projectId: retried.projectId })
      await useAssetStore().refresh({ projectId: retried.projectId })
      const confirmed = this.tasks.find(task => task.id === retried.id)
      if (!confirmed) {
        throw new Error('重试结果待确认')
      }
      return confirmed
    },
    async deleteTask(id: number) {
      const existing = this.tasks.find(task => task.id === id)
      await taskApi.deleteTask(id)
      if (existing?.projectId) {
        await this.refresh({ projectId: existing.projectId })
      } else {
        await this.refresh()
      }
      if (this.tasks.some(task => task.id === id)) {
        throw new Error('删除结果待确认')
      }
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

function normalizeTaskPayload(task: unknown): GenerationTask {
  if (!isPlainObject(task)) {
    throw new Error('任务结果待确认')
  }

  const taskType = typeof task.taskType === 'string' ? task.taskType : ''
  if (taskType !== 'image' && taskType !== 'video') {
    throw new Error('任务结果待确认')
  }

  const status = typeof task.status === 'string' ? task.status : ''
  if (status !== 'pending' && status !== 'running' && status !== 'success' && status !== 'failed') {
    throw new Error('任务结果待确认')
  }

  return {
    id: parseRequiredNumber(task.id),
    projectId: parseRequiredNumber(task.projectId),
    providerId: parseRequiredNumber(task.providerId),
    taskType,
    prompt: typeof task.prompt === 'string' ? task.prompt : '',
    negativePrompt: typeof task.negativePrompt === 'string' && task.negativePrompt ? task.negativePrompt : undefined,
    status,
    progress: normalizeProgress(task.progress) ?? undefined,
    progressText: typeof task.progressText === 'string' ? task.progressText : '',
    modelName: typeof task.modelName === 'string' ? task.modelName : '',
    options: task.options,
    errorMessage: typeof task.errorMessage === 'string' && task.errorMessage ? task.errorMessage : undefined,
    resultAssetId: task.resultAssetId == null ? undefined : parseRequiredNumber(task.resultAssetId),
    requestJson: typeof task.requestJson === 'string' && task.requestJson ? task.requestJson : undefined,
    responseJson: typeof task.responseJson === 'string' && task.responseJson ? task.responseJson : undefined,
    createdAt: String(task.createdAt || '').replace('T', ' ').substring(0, 19),
    finishedAt: task.finishedAt == null ? undefined : String(task.finishedAt).replace('T', ' ').substring(0, 19)
  }
}

function parseRequiredNumber(value: unknown): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('任务结果待确认')
  }
  return parsed
}

function isPlainObject(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
