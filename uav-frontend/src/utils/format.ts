/** 共享日期格式化：后端已统一输出 "yyyy-MM-dd HH:mm:ss" */
export function fmtDateTime(v?: string | null, seconds = false): string {
  if (!v) return '--'
  const s = String(v).replace('T', ' ')
  return seconds ? s.slice(0, 19) : s.slice(0, 16)
}

export function fmtDate(v?: string | null): string {
  if (!v) return '--'
  return String(v).replace('T', ' ').slice(0, 10)
}
