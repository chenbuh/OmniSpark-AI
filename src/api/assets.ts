import request from './request'

export const assetApi = {
  // 获取资产列表，支持按项目 ID 和资产类型过滤
  async getAssets(params?: { projectId?: number; assetType?: string; taskId?: number }) {
    return request.get('/api/assets', {
      params,
      headers: {
        'x-no-cache': '1'
      }
    })
  },

  // 上传参考图或新资产
  async uploadAsset(formData: FormData) {
    return request.post('/api/assets/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
  },

  // 删除资产
  async deleteAsset(id: number) {
    return request.delete(`/api/assets/${id}`)
  },

  // 收藏/取消收藏
  async favoriteAsset(id: number) {
    return request.post(`/api/assets/${id}/favorite`)
  }
}
