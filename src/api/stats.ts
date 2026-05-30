import request from './request'

export interface StatsOverview {
  projectCount: number
  taskCount: number
  successTaskCount: number
  assetCount: number
  favoriteAssetCount: number
  quotaUsed: number
  quotaLimit: number
}

export interface StatsDistribution {
  imageTaskCount: number
  videoTaskCount: number
  successTaskCount: number
  runningTaskCount: number
  failedTaskCount: number
}

export interface StatsTrendPoint {
  date: string
  taskCount: number
  quotaUsed: number
}

export interface StatsProjectRanking {
  rank: number
  projectId: number
  name: string
  description?: string
  taskCount: number
  successTaskCount: number
  successRate: number
  assetCount: number
  quotaUsed: number
  weightPercent: number
  lastActiveAt?: string
}

export interface StatsActivity {
  type: 'image' | 'video' | 'error' | 'quota' | string
  title: string
  description: string
  status: string
  createdAt?: string
}

export interface StatsDashboard {
  overview: StatsOverview
  distribution: StatsDistribution
  trends: StatsTrendPoint[]
  projectRankings: StatsProjectRanking[]
  recentActivities: StatsActivity[]
}

export const statsApi = {
  async getOverview(projectId?: number) {
    return request.get('/api/stats/overview', { params: { projectId } })
  },

  async getDashboard(projectId?: number) {
    return request.get('/api/stats/dashboard', { params: { projectId } })
  }
}
