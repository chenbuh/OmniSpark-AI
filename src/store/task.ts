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
    setTasks(tasks: unknown) {
      this.tasks = normalizeTaskList(tasks, this.normalizeTask)
    },
    upsertTask(task: unknown) {
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
      const response = await taskApi.getTasks(params)
      this.setTasks(getResponseData(response, '任务数据待确认'))
      return this.tasks
    },
    getTasksByProject(projectId: number) {
      return this.tasks.filter(t => t.projectId === projectId)
    },
    async retryTask(id: number) {
      const sourceTask = this.tasks.find(task => task.id === id)
        ?? this.normalizeTask(getResponseData(await taskApi.getTask(id), '任务结果待确认'))
      const response = await taskApi.retryTask(id)
      const retried = this.normalizeTask(getResponseData(response, '任务重试结果待确认'))
      assertRetriedTaskMatchesSource(sourceTask, retried)
      this.upsertTask(retried)
      await this.refresh({ projectId: sourceTask.projectId })
      await useAssetStore().refresh({ projectId: sourceTask.projectId })
      const confirmedFromList = this.tasks.find(task => task.id === retried.id)
      const confirmed = confirmedFromList ?? this.normalizeTask(getResponseData(await taskApi.getTask(retried.id), '任务重试结果待确认'))
      assertRetriedTaskMatchesSource(sourceTask, confirmed)
      assertTaskMatchesExpected(retried, confirmed)
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

  const id = parseRequiredNumber(task.id)
  const projectId = parseRequiredNumber(task.projectId)
  const providerId = parseRequiredNumber(task.providerId)
  const prompt = typeof task.prompt === 'string' ? task.prompt.trim() : ''
  const modelName = typeof task.modelName === 'string' ? task.modelName.trim() : ''
  const createdAt = String(task.createdAt || '').replace('T', ' ').substring(0, 19)
  if (id <= 0 || projectId <= 0 || providerId <= 0 || !prompt || !modelName || !createdAt) {
    throw new Error('任务结果待确认')
  }

  return {
    id,
    projectId,
    providerId,
    taskType,
    prompt,
    negativePrompt: typeof task.negativePrompt === 'string' && task.negativePrompt ? task.negativePrompt : undefined,
    status,
    progress: normalizeProgress(task.progress) ?? undefined,
    progressText: typeof task.progressText === 'string' ? task.progressText : '',
    modelName,
    options: task.options,
    errorMessage: typeof task.errorMessage === 'string' && task.errorMessage ? task.errorMessage : undefined,
    resultAssetId: task.resultAssetId == null ? undefined : parseRequiredNumber(task.resultAssetId),
    requestJson: typeof task.requestJson === 'string' && task.requestJson ? task.requestJson : undefined,
    responseJson: typeof task.responseJson === 'string' && task.responseJson ? task.responseJson : undefined,
    createdAt,
    finishedAt: task.finishedAt == null ? undefined : String(task.finishedAt).replace('T', ' ').substring(0, 19)
  }
}

function normalizeTaskList(tasks: unknown, normalizeTask: (task: unknown) => GenerationTask) {
  if (!Array.isArray(tasks)) {
    throw new Error('任务数据待确认')
  }
  const normalized = tasks.map(item => normalizeTask(item))
  const ids = new Set<number>()
  normalized.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('任务数据待确认')
    }
    ids.add(item.id)
  })
  return normalized
}

function parseRequiredNumber(value: unknown): number {
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) {
    throw new Error('任务结果待确认')
  }
  return parsed
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
}

function assertRetriedTaskMatchesSource(source: GenerationTask, retried: GenerationTask) {
  if (retried.id === source.id) {
    throw new Error('任务重试结果待确认')
  }
  if (retried.projectId !== source.projectId) {
    throw new Error('任务重试结果待确认')
  }
  if (retried.providerId !== source.providerId) {
    throw new Error('任务重试结果待确认')
  }
  if (retried.taskType !== source.taskType) {
    throw new Error('任务重试结果待确认')
  }
  if (retried.prompt !== source.prompt) {
    throw new Error('任务重试结果待确认')
  }
  if ((retried.negativePrompt ?? '') !== (source.negativePrompt ?? '')) {
    throw new Error('任务重试结果待确认')
  }
  if (retried.modelName !== source.modelName) {
    throw new Error('任务重试结果待确认')
  }
}

function assertTaskMatchesExpected(expected: GenerationTask, actual: GenerationTask) {
  if (actual.id !== expected.id) {
    throw new Error('任务重试结果待确认')
  }
  if (actual.projectId !== expected.projectId) {
    throw new Error('任务重试结果待确认')
  }
  if (actual.providerId !== expected.providerId) {
    throw new Error('任务重试结果待确认')
  }
  if (actual.taskType !== expected.taskType) {
    throw new Error('任务重试结果待确认')
  }
  if (actual.prompt !== expected.prompt) {
    throw new Error('任务重试结果待确认')
  }
  if ((actual.negativePrompt ?? '') !== (expected.negativePrompt ?? '')) {
    throw new Error('任务重试结果待确认')
  }
  if (actual.modelName !== expected.modelName) {
    throw new Error('任务重试结果待确认')
  }
}
