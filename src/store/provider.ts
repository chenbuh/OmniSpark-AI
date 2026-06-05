import { defineStore } from 'pinia'
import { providerApi } from '@/api/providers'

export interface ModelProvider {
  id: number
  projectId: number
  name: string
  type: string
  baseUrl: string
  apiKey: string
  modelName: string
  enabled: boolean | null
  isDefault: boolean | null
  configJson?: string
}

export const useModelProviderStore = defineStore('modelProvider', {
  state: () => ({
    providers: [] as ModelProvider[]
  }),
  actions: {
    normalizeProvider(provider: any): ModelProvider {
      return {
        id: Number(provider.id),
        projectId: Number(provider.projectId),
        name: provider.name || '',
        type: provider.type || '',
        baseUrl: provider.baseUrl || '',
        apiKey: provider.apiKey || '',
        modelName: provider.modelName || '',
        enabled: parseOptionalBoolean(provider.enabled),
        isDefault: parseOptionalBoolean(provider.isDefault),
        configJson: provider.configJson || ''
      }
    },
    setProviders(providers: any[]) {
      this.providers = providers.map(item => this.normalizeProvider(item))
    },
    async refresh(projectId?: number) {
      const res = await providerApi.getProviders(projectId)
      if (!Array.isArray(res.data)) {
        throw new Error('模型提供商数据待确认')
      }
      this.setProviders(res.data)
      return this.providers
    },
    getProvidersByProject(projectId: number) {
      return this.providers.filter(p => p.projectId === projectId || p.projectId === 0)
    },
    async addProvider(provider: Omit<ModelProvider, 'id'>) {
      const res = await providerApi.createProvider(provider)
      const beforeIds = new Set(
        this.getProvidersByProject(provider.projectId)
          .map(item => item.id)
          .filter(id => Number.isFinite(id) && id > 0)
      )
      await this.refresh(provider.projectId)
      const expected = {
        projectId: provider.projectId,
        name: provider.name,
        type: provider.type,
        baseUrl: provider.baseUrl,
        modelName: provider.modelName,
        configJson: provider.configJson,
        enabled: provider.enabled,
        isDefault: provider.isDefault
      }
      const responseId = parseRequiredProviderId((res as any).data?.id)
      if (responseId !== null) {
        const createdById = this.providers.find(item => item.id === responseId)
        if (createdById) {
          return assertProviderMatchesExpected(createdById, expected, 'create')
        }
      }
      const createdByDiff = this.getProvidersByProject(provider.projectId).find(item =>
        !beforeIds.has(item.id)
        && item.name === provider.name
        && item.type === provider.type
        && item.baseUrl === provider.baseUrl
        && item.modelName === provider.modelName
      )
      if (createdByDiff) {
        return assertProviderMatchesExpected(createdByDiff, expected, 'create')
      }
      throw new Error('模型提供商创建结果待确认')
    },
    async updateProvider(id: number, updated: Partial<ModelProvider>) {
      const res = await providerApi.updateProvider(id, updated)
      const current = this.providers.find(p => p.id === id)
      const projectId = updated.projectId ?? current?.projectId
      await this.refresh(projectId)
      const responseId = parseRequiredProviderId((res as any).data?.id)
      const resolvedId = responseId ?? id
      const refreshed = this.providers.find(item => item.id === resolvedId)
      if (!refreshed) {
        throw new Error('模型提供商更新结果待确认')
      }
      return assertProviderMatchesExpected(refreshed, {
        projectId: projectId ?? refreshed.projectId,
        name: updated.name ?? current?.name ?? refreshed.name,
        type: updated.type ?? current?.type ?? refreshed.type,
        baseUrl: updated.baseUrl ?? current?.baseUrl ?? refreshed.baseUrl,
        modelName: updated.modelName ?? current?.modelName ?? refreshed.modelName,
        configJson: updated.configJson ?? current?.configJson ?? refreshed.configJson,
        enabled: updated.enabled,
        isDefault: updated.isDefault
      }, 'update')
    },
    async deleteProvider(id: number) {
      const current = this.providers.find(p => p.id === id)
      await providerApi.deleteProvider(id)
      if (current) {
        await this.refresh(current.projectId)
        if (this.providers.some(item => item.id === id)) {
          throw new Error('模型提供商删除结果待确认')
        }
        return
      }
      this.providers = this.providers.filter(p => p.id !== id)
    },
    async setDefaultProvider(id: number) {
      const provider = this.providers.find(p => p.id === id)
      if (!provider) return
      await providerApi.updateProvider(id, { isDefault: true })
      await this.refresh(provider.projectId)
      const refreshed = this.providers.find(item => item.id === id)
      if (!refreshed || refreshed.isDefault !== true) {
        throw new Error('默认提供商状态待确认')
      }
      const conflict = this.providers.find(item =>
        item.id !== id
        && item.projectId === provider.projectId
        && item.type === provider.type
        && item.isDefault === true
      )
      if (conflict) {
        throw new Error('默认提供商状态待确认')
      }
    },
    clear() {
      this.providers = []
    },
    async testConnection(id: number): Promise<boolean> {
      const res = await providerApi.testConnection(id)
      const result = (res as any).data
      if (typeof result !== 'string' || result.trim() !== '连接成功') {
        throw new Error('连接测试结果待确认')
      }
      return true
    }
  }
})

function assertProviderMatchesExpected(
  provider: ModelProvider,
  expected: {
    projectId?: number
    name?: string
    type?: string
    baseUrl?: string
    modelName?: string
    configJson?: string
    enabled?: boolean | null
    isDefault?: boolean | null
  },
  action: 'create' | 'update'
) {
  const errorMessage = action === 'create' ? '模型提供商创建结果待确认' : '模型提供商更新结果待确认'
  if (typeof expected.projectId === 'number' && provider.projectId !== expected.projectId) {
    throw new Error(errorMessage)
  }
  if (typeof expected.name === 'string' && normalizeOptionalText(provider.name) !== normalizeOptionalText(expected.name)) {
    throw new Error(errorMessage)
  }
  if (typeof expected.type === 'string' && normalizeOptionalText(provider.type) !== normalizeOptionalText(expected.type)) {
    throw new Error(errorMessage)
  }
  if (typeof expected.baseUrl === 'string' && normalizeOptionalText(provider.baseUrl) !== normalizeOptionalText(expected.baseUrl)) {
    throw new Error(errorMessage)
  }
  if (typeof expected.modelName === 'string' && normalizeOptionalText(provider.modelName) !== normalizeOptionalText(expected.modelName)) {
    throw new Error(errorMessage)
  }
  if (typeof expected.configJson === 'string' && normalizeOptionalText(provider.configJson) !== normalizeOptionalText(expected.configJson)) {
    throw new Error(errorMessage)
  }
  if (typeof expected.enabled === 'boolean' && provider.enabled !== expected.enabled) {
    throw new Error(errorMessage)
  }
  if (typeof expected.isDefault === 'boolean' && provider.isDefault !== expected.isDefault) {
    throw new Error(errorMessage)
  }
  return provider
}

function parseRequiredProviderId(value: unknown): number | null {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function parseOptionalBoolean(value: unknown): boolean | null {
  if (value == null || value === '') {
    return null
  }
  if (typeof value === 'boolean') {
    return value
  }
  if (typeof value === 'number') {
    return value !== 0
  }
  const normalized = String(value).trim().toLowerCase()
  if (!normalized) {
    return null
  }
  if (normalized === '0' || normalized === 'false') {
    return false
  }
  if (normalized === '1' || normalized === 'true') {
    return true
  }
  return null
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}
