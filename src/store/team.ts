import { defineStore } from 'pinia'
import { teamApi, type Team, type TeamMember } from '@/api/teams'

export const useTeamStore = defineStore('team', {
  state: () => ({
    teams: [] as Team[],
    currentMembers: [] as TeamMember[]
  }),
  actions: {
    normalizeTeam(team: any): Team {
      return {
        id: Number(team.id),
        name: team.name || '',
        description: team.description || undefined,
        ownerId: Number(team.ownerId),
        ownerName: team.ownerName || undefined,
        avatar: team.avatar || undefined,
        status: parseOptionalNumber(team.status),
        memberCount: team.memberCount == null ? undefined : Number(team.memberCount),
        createdAt: String(team.createdAt || '').replace('T', ' ').substring(0, 19)
      }
    },
    normalizeMember(m: any): TeamMember {
      return {
        id: Number(m.id),
        teamId: Number(m.teamId),
        userId: Number(m.userId),
        username: m.username || '',
        nickname: m.nickname || undefined,
        avatar: m.avatar || undefined,
        role: typeof m.role === 'string' ? m.role.trim() : '',
        status: parseOptionalNumber(m.status),
        createdAt: String(m.createdAt || '').replace('T', ' ').substring(0, 19)
      }
    },
    setTeams(data: any[]) {
      this.teams = data.map(item => this.normalizeTeam(item))
    },
    setMembers(data: any[]) {
      this.currentMembers = data.map(item => this.normalizeMember(item))
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
  return Number.isNaN(parsed) ? null : parsed
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
