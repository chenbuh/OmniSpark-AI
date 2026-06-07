import { defineStore } from 'pinia'
import { projectApi } from '@/api/projects'

export type ProjectAccessPermission = 'owner' | 'admin' | 'edit' | 'view'

export interface Project {
  id: number
  userId: number
  name: string
  description?: string
  status: number | null
  createdAt: string
  accessPermission: ProjectAccessPermission
  ownedByCurrentUser: boolean
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
      const ownedByCurrentUser = parseBoolean(project.ownedByCurrentUser)
      const accessPermission = normalizeProjectAccessPermission(project.accessPermission)
      if (status != null && status < 0) {
        throw new Error('项目数据待确认')
      }
      if ((ownedByCurrentUser && accessPermission !== 'owner') || (!ownedByCurrentUser && accessPermission === 'owner')) {
        throw new Error('项目数据待确认')
      }
      return {
        id,
        userId,
        name,
        description: normalizeOptionalText(project?.description),
        status,
        createdAt,
        accessPermission,
        ownedByCurrentUser
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
        const pageSize = 100
        const projects = await collectAllProjectPages(pageSize, this.normalizeProject)
        this.setProjects(projects)
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
      const response = await projectApi.createProject({ name, description })
      const responseData = getResponseData(response, '项目创建结果待确认')
      const createdProject = this.normalizeProject(responseData)
      if (
        normalizeOptionalText(createdProject.name) !== normalizeOptionalText(name)
        || normalizeOptionalText(createdProject.description) !== normalizeOptionalText(description)
      ) {
        throw new Error('项目创建结果待确认')
      }

      const existingIndex = this.projects.findIndex(item => item.id === createdProject.id)
      if (existingIndex === -1) {
        this.projects.unshift(createdProject)
      } else {
        this.projects[existingIndex] = createdProject
      }
      this.activeProjectId = createdProject.id
      if (this.loadState === 'idle') {
        this.loadState = 'ready'
      }
      return createdProject
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

function getResponseData(response: unknown, errorMessage: string) {
  if (!isPlainObject(response) || !('data' in response)) {
    throw new Error(errorMessage)
  }
  return response.data
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

async function collectAllProjectPages(
  pageSize: number,
  normalizeProject: (project: unknown) => Project
): Promise<Project[]> {
  const allProjects: Project[] = []
  const seenIds = new Set<number>()
  let page = 1
  let total: number | null = null

  while (true) {
    const response = await projectApi.getProjectsPage({ page, pageSize })
    const pageData = normalizeProjectPage(getResponseData(response, '项目数据待确认'), normalizeProject)
    if (total === null) {
      total = pageData.total
    } else if (pageData.total !== total) {
      throw new Error('项目数据待确认')
    }
    pageData.records.forEach(project => {
      if (seenIds.has(project.id)) {
        throw new Error('项目数据待确认')
      }
      seenIds.add(project.id)
      allProjects.push(project)
    })
    if (allProjects.length > pageData.total) {
      throw new Error('项目数据待确认')
    }
    if (allProjects.length >= pageData.total || pageData.records.length === 0) {
      break
    }
    page += 1
  }

  if ((total ?? 0) !== allProjects.length) {
    throw new Error('项目数据待确认')
  }
  return allProjects
}

function normalizeProjectPage(
  value: unknown,
  normalizeProject: (project: unknown) => Project
): { total: number; records: Project[] } {
  if (!isPlainObject(value)) {
    throw new Error('项目数据待确认')
  }
  const total = Number(value.total)
  const records = normalizeProjectList(value.records, normalizeProject)
  if (!Number.isFinite(total) || total < 0 || records.length > total) {
    throw new Error('项目数据待确认')
  }
  return { total, records }
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

function normalizeProjectAccessPermission(value: unknown): ProjectAccessPermission {
  if (value === 'owner' || value === 'admin' || value === 'edit' || value === 'view') {
    return value
  }
  throw new Error('项目数据待确认')
}

function parseBoolean(value: unknown): boolean {
  if (value === true || value === 'true' || value === 1 || value === '1') {
    return true
  }
  if (value === false || value === 'false' || value === 0 || value === '0') {
    return false
  }
  throw new Error('项目数据待确认')
}
