import { request } from '@/utils/request'
import type { PagedResult } from './predict'
import type { PredictionResult } from '@/types'

/** 收藏状态响应 */
export interface FavoriteStatus {
  favorited: boolean
}

export const favoriteApi = {
  /** 收藏指定结果 */
  add(resultId: string | number): Promise<void> {
    return request.post<void>('/api/favorites', { resultId })
  },

  /** 取消收藏 */
  remove(resultId: string | number): Promise<void> {
    return request.delete<void>(`/api/favorites/${resultId}`)
  },

  /** 查询是否已收藏 */
  status(resultId: string | number): Promise<FavoriteStatus> {
    return request.get<FavoriteStatus>(`/api/favorites/${resultId}/status`)
  },

  /** 分页查询收藏结果 */
  list(page = 1, pageSize = 100): Promise<PagedResult<PredictionResult>> {
    return request.get<PagedResult<PredictionResult>>('/api/favorites', { params: { page, pageSize } })
  }
}
