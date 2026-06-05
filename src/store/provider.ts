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
      await this.refresh(provider.projectId)
      return this.normalizeProvider(res.data)
    },
    async updateProvider(id: number, updated: Partial<ModelProvider>) {
      const res = await providerApi.updateProvider(id, updated)
      const current = this.providers.find(p => p.id === id)
      await this.refresh(updated.projectId ?? current?.projectId)
      return this.normalizeProvider(res.data)
    },
    async deleteProvider(id: number) {
      await providerApi.deleteProvider(id)
      this.providers = this.providers.filter(p => p.id !== id)
    },
    async setDefaultProvider(id: number) {
      const provider = this.providers.find(p => p.id === id)
      if (!provider) return
      await providerApi.updateProvider(id, { isDefault: true })
      await this.refresh(provider.projectId)
    },
    clear() {
      this.providers = []
    },
    async testConnection(id: number): Promise<boolean> {
      await providerApi.testConnection(id)
      return true
    }
  }
})

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
