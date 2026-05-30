import { defineStore } from 'pinia'
import { providerApi } from '@/api/providers'

export interface ModelProvider {
  id: number
  projectId: number
  name: string
  type: 'openai' | 'image' | 'video' | 'custom'
  baseUrl: string
  apiKey: string
  modelName: string
  enabled: boolean
  isDefault: boolean
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
        type: provider.type || 'custom',
        baseUrl: provider.baseUrl || '',
        apiKey: provider.apiKey || '',
        modelName: provider.modelName || '',
        enabled: Number(provider.enabled ?? 1) !== 0,
        isDefault: Number(provider.isDefault ?? 0) !== 0
      }
    },
    setProviders(providers: any[]) {
      this.providers = providers.map(item => this.normalizeProvider(item))
    },
    async refresh(projectId?: number) {
      const res = await providerApi.getProviders(projectId)
      this.setProviders(res.data || [])
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
