import { defineStore } from 'pinia'
import { teamApi, type Team, type TeamMember } from '@/api/teams'

export const useTeamStore = defineStore('team', {
  state: () => ({
    teams: [] as Team[],
    currentMembers: [] as TeamMember[]
  }),
  actions: {
    normalizeTeam(team: unknown): Team {
      if (!team || typeof team !== 'object' || Array.isArray(team)) {
        throw new Error('团队数据待确认')
      }
      const record = team as Record<string, unknown>
      const id = parseRequiredPositiveNumber(record.id, '团队数据待确认')
      const ownerId = parseRequiredPositiveNumber(record.ownerId, '团队数据待确认')
      const name = normalizeOptionalText(record.name)
      if (!name) {
        throw new Error('团队数据待确认')
      }
      const memberCountValue = parseOptionalNumber(record.memberCount)
      if (memberCountValue != null && memberCountValue < 0) {
        throw new Error('团队数据待确认')
      }
      return {
        id,
        name,
        description: normalizeOptionalText(record.description) || undefined,
        ownerId,
        ownerName: normalizeOptionalText(record.ownerName) || undefined,
        avatar: normalizeOptionalText(record.avatar) || undefined,
        status: parseOptionalNumber(record.status),
        memberCount: memberCountValue ?? undefined,
        createdAt: normalizeDateTime(record.createdAt)
      }
    },
    normalizeMember(m: unknown): TeamMember {
      if (!m || typeof m !== 'object' || Array.isArray(m)) {
        throw new Error('成员数据待确认')
      }
      const record = m as Record<string, unknown>
      const id = parseRequiredPositiveNumber(record.id, '成员数据待确认')
      const teamId = parseRequiredPositiveNumber(record.teamId, '成员数据待确认')
      const userId = parseRequiredPositiveNumber(record.userId, '成员数据待确认')
      const username = normalizeOptionalText(record.username)
      const role = normalizeOptionalText(record.role)
      if (!username || !role) {
        throw new Error('成员数据待确认')
      }
      return {
        id,
        teamId,
        userId,
        username,
        nickname: normalizeOptionalText(record.nickname) || undefined,
        avatar: normalizeOptionalText(record.avatar) || undefined,
        role,
        status: parseOptionalNumber(record.status),
        createdAt: normalizeDateTime(record.createdAt)
      }
    },
    setTeams(data: any[]) {
      const normalized = data.map(item => this.normalizeTeam(item))
      const ids = new Set<number>()
      normalized.forEach(item => {
        if (ids.has(item.id)) {
          throw new Error('团队数据待确认')
        }
        ids.add(item.id)
      })
      this.teams = normalized
    },
    setMembers(data: any[]) {
      const normalized = data.map(item => this.normalizeMember(item))
      const ids = new Set<number>()
      normalized.forEach(item => {
        if (ids.has(item.id)) {
          throw new Error('成员数据待确认')
        }
        ids.add(item.id)
      })
      this.currentMembers = normalized
    },
    async refresh() {
      const res = await teamApi.getTeams()
      if (!Array.isArray(res.data)) {
        throw new Error('团队数据待确认')
      }
      this.setTeams(res.data)
      return this.teams
    },
    async refreshMembers(teamId: number) {
      const res = await teamApi.getMembers(teamId)
      if (!Array.isArray(res.data)) {
        throw new Error('成员数据待确认')
      }
      this.setMembers(res.data)
      if (this.currentMembers.some(item => item.teamId !== teamId)) {
        throw new Error('成员数据待确认')
      }
      return this.currentMembers
    },
    async createTeam(name: string, description?: string) {
      const res = await teamApi.createTeam({ name, description })
      const beforeIds = new Set(
        this.teams
          .map(item => item.id)
          .filter(id => Number.isFinite(id) && id > 0)
      )
      await this.refresh()
      const expectedName = normalizeOptionalText(name)
      const expectedDescription = normalizeOptionalText(description)
      const hasExpectedFields = (team: Team) =>
        normalizeOptionalText(team.name) === expectedName
        && normalizeOptionalText(team.description) === expectedDescription
      const responseId = parseRequiredTeamId((res as any).data?.id)
      if (responseId !== null) {
        const createdById = this.teams.find(item => item.id === responseId)
        if (createdById && hasExpectedFields(createdById)) {
          return createdById
        }
      }
      const createdByDiff = this.teams.find(item =>
        !beforeIds.has(item.id)
        && hasExpectedFields(item)
      )
      if (createdByDiff) {
        return createdByDiff
      }
      throw new Error('团队创建结果待确认')
    },
    async deleteTeam(id: number) {
      await teamApi.deleteTeam(id)
      await this.refresh()
      if (this.teams.some(team => team.id === id)) {
        throw new Error('团队删除结果待确认')
      }
    },
    clear() {
      this.teams = []
      this.currentMembers = []
    }
  }
})

function parseOptionalNumber(value: unknown): number | null {
  if (value == null || value === '') {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function parseRequiredTeamId(value: unknown): number | null {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return null
  }
  return parsed
}

function normalizeOptionalText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function parseRequiredPositiveNumber(value: unknown, errorMessage: string): number {
  const parsed = parseRequiredTeamId(value)
  if (parsed == null) {
    throw new Error(errorMessage)
  }
  return parsed
}

function normalizeDateTime(value: unknown): string {
  return String(value || '').replace('T', ' ').substring(0, 19)
}
