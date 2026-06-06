import request from './request'

export type WorkflowStepType = 'image' | 'video' | 'subtitle'

export interface WorkflowStep {
  type: string
  prompt: string
  description?: string
  providerId?: number | null
  modelName?: string
  negativePrompt?: string
  size?: string
  count?: number
  duration?: string
  sourceAssetId?: number | null
  endAssetId?: number | null
  usePreviousAsReference?: boolean
  language?: string
  voice?: boolean
  options?: Record<string, unknown>
}

export interface WorkflowVO {
  id: number
  projectId: number
  name: string
  description?: string
  stepsJson: string
  status: number
  createdAt?: string
}

export interface WorkflowRunVO {
  id: number
  workflowId: number
  projectId: number
  status: string
  currentStep?: number
  stepsResultJson?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

export interface WorkflowMetaOption {
  label: string
  value: string
}

export interface WorkflowMetaVO {
  stepTypes: WorkflowMetaOption[]
  imageSizes: WorkflowMetaOption[]
  videoDurations: WorkflowMetaOption[]
  subtitleLanguages: WorkflowMetaOption[]
  defaults?: {
    imageSize?: string
    videoDuration?: string
    subtitleLanguage?: string
  }
}

export const workflowApi = {
  async meta() {
    return request.get('/api/workflows/meta')
  },

  async list(projectId?: number) {
    return request.get('/api/workflows', { params: { projectId } })
  },

  async get(id: number) {
    return request.get(`/api/workflows/${id}`)
  },

  async create(payload: { projectId: number; name: string; description?: string; stepsJson: string }) {
    return request.post('/api/workflows', payload)
  },

  async update(id: number, payload: { projectId: number; name: string; description?: string; stepsJson: string }) {
    return request.put(`/api/workflows/${id}`, payload)
  },

  async remove(id: number) {
    return request.delete(`/api/workflows/${id}`)
  },

  async execute(id: number) {
    return request.post(`/api/workflows/${id}/execute`)
  },

  async listRuns(id: number) {
    return request.get(`/api/workflows/${id}/runs`)
  }
}
