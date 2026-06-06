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
    normalizeProject(project: unknown): Project {
      if (!isPlainObject(project)) {
        throw new Error('项目数据待确认')
      }
      const id = parseRequiredProjectNumber(project?.id)
      const userId = parseRequiredProjectNumber(project?.userId)
      const name = normalizeRequiredText(project?.name, '项目数据待确认')
      const status = parseOptionalNumber(project.status)
      const createdAt = normalizeRequiredDateTime(project.createdAt, '项目数据待确认')
      if (status != null && status < 0) {
        throw new Error('项目数据待确认')
      }
      return {
        id,
        userId,
        name,
        description: normalizeOptionalText(project?.description),
        status,
        createdAt
      }
    },
    setProjects(projects: unknown) {
      this.projects = normalizeProjectList(projects, this.normalizeProject)
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
      const expectedName = normalizeOptionalText(name)
      const expectedDescription = normalizeOptionalText(description)
      const hasExpectedFields = (project: Project) =>
        normalizeOptionalText(project.name) === expectedName
        && normalizeOptionalText(project.description) === expectedDescription
      if (createdId != null) {
        const createdById = this.projects.find(item => item.id === createdId)
        if (createdById && hasExpectedFields(createdById)) {
          this.activeProjectId = createdById.id
          return createdById
        }
      }
      const createdProject = this.projects.find(item =>
        !previousProjectIds.has(item.id)
        && hasExpectedFields(item)
      )
      if (createdProject) {
        this.activeProjectId = createdProject.id
        return createdProject
      }
      throw new Error('项目创建结果待确认')
    },
    async updateProject(id: number, name: string, description: string) {
      await projectApi.updateProject(id, { name, description })
      await this.refresh()
      const expectedName = normalizeOptionalText(name)
      const expectedDescription = normalizeOptionalText(description)
      const confirmedProject = this.projects.find(item => item.id === id)
      if (
        !confirmedProject
        || normalizeOptionalText(confirmedProject.name) !== expectedName
        || normalizeOptionalText(confirmedProject.description) !== expectedDescription
      ) {
        throw new Error('项目更新结果待确认')
      }
      return confirmedProject
    },
    async deleteProject(id: number) {
      await projectApi.deleteProject(id)
      await this.refresh()
      if (this.projects.some(project => project.id === id)) {
        throw new Error('项目删除结果待确认')
      }
    },
    clear() {
      this.projects = []
      this.activeProjectId = 0
      this.loadState = 'idle'
    }
  }
})

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function normalizeProjectList(
  value: unknown,
  normalizeProject: (project: unknown) => Project
): Project[] {
  if (!Array.isArray(value)) {
    throw new Error('项目数据待确认')
  }
  const normalized = value.map(item => normalizeProject(item))
  const ids = new Set<number>()
  normalized.forEach(item => {
    if (ids.has(item.id)) {
      throw new Error('项目数据待确认')
    }
    ids.add(item.id)
  })
  return normalized
}

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

function parseRequiredProjectNumber(value: unknown): number {
  const parsed = parseRequiredProjectId(value)
  if (parsed == null) {
    throw new Error('项目数据待确认')
  }
  return parsed
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeRequiredText(value: unknown, errorMessage: string): string {
  const normalized = normalizeOptionalText(value)
  if (!normalized) {
    throw new Error(errorMessage)
  }
  return normalized
}

function normalizeRequiredDateTime(value: unknown, errorMessage: string): string {
  const normalized = normalizeOptionalText(value).replace('T', ' ').substring(0, 19)
  if (!normalized) {
    throw new Error(errorMessage)
  }
  return normalized
}
