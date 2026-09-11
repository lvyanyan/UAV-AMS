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
  meta: (): Promise<R<Array<{ dictType: string; dictName: string; businessGroup: string; sort?: number }>>> =>
    request.get('/dict/meta'),
  create: (data: { dictType: string; dictValue: string; dictLabel: string; sortOrder?: number }): Promise<R<string>> =>
    request.post('/dict', data),
  update: (id: number, data: { dictLabel: string; sortOrder?: number }): Promise<R<string>> =>
    request.put(`/dict/${id}`, data),
  remove: (id: number): Promise<R<string>> => request.delete(`/dict/${id}`),
}
