import request from './request'

export interface Team {
  id: number
  name: string
  description?: string
  ownerId: number
  ownerName?: string
  avatar?: string
  status: number | null
  memberCount?: number
  createdAt: string
}

export interface TeamMember {
  id: number
  teamId: number
  userId: number
  username: string
  nickname?: string
  avatar?: string
  role: string
  status: number | null
  createdAt: string
}

export interface PageResult<T> {
  total: number
  pages: number
  records: T[]
}

export const teamApi = {
  async getTeams() {
    return request.get('/api/teams')
  },

  async getTeamsPage(params: { page: number; pageSize: number }) {
    return request.get('/api/teams/page', { params })
  },

  async getTeam(id: number) {
    return request.get(`/api/teams/${id}`)
  },

  async createTeam(params: { name: string; description?: string }) {
    return request.post('/api/teams', params)
  },

  async updateTeam(id: number, params: { name: string; description?: string }) {
    return request.put(`/api/teams/${id}`, params)
  },

  async deleteTeam(id: number) {
    return request.delete(`/api/teams/${id}`)
  },

  async getMembers(teamId: number) {
    return request.get(`/api/teams/${teamId}/members`)
  },

  async getMembersPage(teamId: number, params: { page: number; pageSize: number }) {
    return request.get(`/api/teams/${teamId}/members/page`, { params })
  },

  async getMember(teamId: number, userId: number) {
    return request.get(`/api/teams/${teamId}/members/${userId}`)
  },

  async inviteMember(params: { teamId: number; username: string; role?: string }) {
    return request.post('/api/teams/members/invite', params)
  },

  async removeMember(teamId: number, userId: number) {
    return request.delete(`/api/teams/${teamId}/members/${userId}`)
  },

  async leaveTeam(teamId: number) {
    return request.post(`/api/teams/${teamId}/leave`)
  }
}
