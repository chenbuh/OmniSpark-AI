import request from './request'

export interface DataDictItem {
  id: number
  dictId: number
  itemCode: string
  itemName: string
  sortOrder?: number
  status?: number
}

export const dictApi = {
  async getItems(dictCode: string, limit = 100) {
    return request.get<DataDictItem[]>(`/api/dict/${encodeURIComponent(dictCode)}/items`, {
      params: { limit }
    })
  }
}
