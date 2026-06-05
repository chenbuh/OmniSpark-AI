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
        nickname: m.nickname || m.username || '',
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
      this.setTeams(res.data || [])
      return this.teams
    },
    async refreshMembers(teamId: number) {
      const res = await teamApi.getMembers(teamId)
      this.setMembers(res.data || [])
      return this.currentMembers
    },
    async createTeam(name: string, description?: string) {
      const res = await teamApi.createTeam({ name, description })
      await this.refresh()
      return this.normalizeTeam(res.data)
    },
    async deleteTeam(id: number) {
      await teamApi.deleteTeam(id)
      this.teams = this.teams.filter(t => t.id !== id)
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
