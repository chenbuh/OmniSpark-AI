import request from './request'

export interface SubtitleVO {
  id: number
  assetId: number
  projectId: number
  language: string
  srtContent: string
  status: number
  voiceUrl?: string
  createdAt: string
}

export const subtitleApi = {
  async list(assetId: number) {
    return request.get(`/api/video/subtitles/${assetId}`)
  },

  async generate(params: { assetId: number; projectId: number; prompt: string; language?: string }) {
    return request.post('/api/video/subtitles/generate', params)
  },

  async update(id: number, params: { srtContent: string; language?: string }) {
    return request.put('/api/video/subtitles', { id, ...params })
  },

  async generateVoice(id: number) {
    return request.post(`/api/video/subtitles/${id}/voice`)
  },

  async delete(id: number) {
    return request.delete(`/api/video/subtitles/${id}`)
  }
}
