import request from './request'

export interface StatsOverview {
  projectCount?: number | null
  taskCount?: number | null
  successTaskCount?: number | null
  assetCount?: number | null
  favoriteAssetCount?: number | null
  quotaUsed?: number | null
  quotaLimit?: number | null
}

export interface StatsDistribution {
  imageTaskCount?: number | null
  videoTaskCount?: number | null
  successTaskCount?: number | null
  runningTaskCount?: number | null
  failedTaskCount?: number | null
}

export interface StatsTrendPoint {
  date: string
  taskCount?: number | null
  quotaUsed?: number | null
}

export interface StatsProjectRanking {
  rank?: number | null
  projectId?: number | null
  name: string
  description?: string
  taskCount?: number | null
  successTaskCount?: number | null
  successRate?: number | null
  assetCount?: number | null
  quotaUsed?: number | null
  weightPercent?: number | null
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
  scope?: string
  message?: string
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
