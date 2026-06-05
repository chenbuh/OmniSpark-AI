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
    activeProjectId: 0,
    loadState: 'idle' as 'idle' | 'loading' | 'ready' | 'error'
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
      this.loadState = 'loading'
      try {
        const res = await projectApi.getProjects()
        if (!Array.isArray(res.data)) {
          throw new Error('项目数据待确认')
        }
        this.setProjects(res.data)
        this.loadState = 'ready'
        return this.projects
      } catch (error) {
        this.loadState = 'error'
        throw error
      }
    },
    setActiveProject(id: number) {
      this.activeProjectId = id
    },
    async addProject(name: string, description: string) {
      const previousProjectIds = new Set(this.projects.map(item => item.id))
      const res = await projectApi.createProject({ name, description })
      await this.refresh()
      const createdId = parseRequiredProjectId(res.data?.id)
      if (createdId == null) {
        const createdProject = this.projects.find(item => !previousProjectIds.has(item.id) && item.name === name)
        if (createdProject) {
          this.activeProjectId = createdProject.id
          return createdProject
        }
        throw new Error('项目创建结果待确认')
      }
      this.activeProjectId = createdId
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
      this.loadState = 'idle'
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

function parseRequiredProjectId(value: unknown): number | null {
  const parsed = parseOptionalNumber(value)
  return parsed != null && parsed > 0 ? parsed : null
}
