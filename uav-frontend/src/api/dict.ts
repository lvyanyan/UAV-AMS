import request from './index'
import type { R } from './index'

export interface DictItem {
  id?: number
  value: string
  label: string
  sort?: number
  dictType?: string
}

export const dictApi = {
  /** 全量字典：{ dictType: DictItem[] } */
  all: (): Promise<R<Record<string, DictItem[]>>> => request.get('/dict/all'),
  byType: (type: string): Promise<R<DictItem[]>> => request.get(`/dict/data/${type}`),
  create: (data: { dictType: string; dictValue: string; dictLabel: string; sortOrder?: number }): Promise<R<string>> =>
    request.post('/dict', data),
  update: (id: number, data: { dictLabel: string; sortOrder?: number }): Promise<R<string>> =>
    request.put(`/api/dict/${id}`, data),
  remove: (id: number): Promise<R<string>> => request.delete(`/api/dict/${id}`),
}
