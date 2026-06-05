import { defineStore } from 'pinia'
import { projectApi } from '@/api/projects'

export interface Project {
  id: number
  userId: number
  name: string
  description?: string
  status: number | null
  createdAt: string
}

export const useProjectStore = defineStore('project', {
  state: () => ({
    projects: [] as Project[],
    activeProjectId: 0
  }),
  actions: {
    normalizeProject(project: any): Project {
      return {
        id: Number(project.id),
        userId: Number(project.userId),
        name: project.name || '',
        description: project.description || '',
        status: parseOptionalNumber(project.status),
        createdAt: String(project.createdAt || '').replace('T', ' ').substring(0, 19)
      }
    },
    setProjects(projects: any[]) {
      this.projects = projects.map(item => this.normalizeProject(item))
      if (this.projects.length > 0) {
        const exists = this.projects.some(item => item.id === this.activeProjectId)
        if (!exists) {
          this.activeProjectId = this.projects[0].id
        }
      } else {
        this.activeProjectId = 0
      }
    },
    async refresh() {
      const res = await projectApi.getProjects()
      this.setProjects(res.data || [])
      return this.projects
    },
    setActiveProject(id: number) {
      this.activeProjectId = id
    },
    async addProject(name: string, description: string) {
      const res = await projectApi.createProject({ name, description })
      await this.refresh()
      this.activeProjectId = Number(res.data?.id || this.activeProjectId)
      return res.data
    },
    async updateProject(id: number, name: string, description: string) {
      const res = await projectApi.updateProject(id, { name, description })
      await this.refresh()
      return res.data
    },
    async deleteProject(id: number) {
      await projectApi.deleteProject(id)
      await this.refresh()
    },
    clear() {
      this.projects = []
      this.activeProjectId = 0
    }
  }
})

function parseOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isNaN(parsed) ? null : parsed
}
