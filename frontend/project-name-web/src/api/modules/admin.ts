// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { apiRequest } from '@/api/client'
import type { AuditLogEntry, AuditLogFilters, CursorPage } from '@/types/admin'

const query = (values: Record<string, string | number | null>) => {
  const result = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && String(value).trim()) result.set(key, String(value))
  })
  return result.toString()
}

/**
 * 管理面只剩审计流水。本地账号管理（/api/admin/users*）2026-09-15 随本地账号体系退役：
 * 账号与身份归平台 IdP，那组接口管理的是再也登录不了的账号。
 */
export const adminApi = {
  /** 无界流水，返回 {items, nextCursor}（通则 A-3）。 */
  listAuditLogs: (filters: AuditLogFilters) =>
    apiRequest<CursorPage<AuditLogEntry>>(`/api/admin/audit-logs?${query({
      limit: filters.limit,
      cursor: filters.cursor,
      keyword: filters.keyword,
      actionCode: filters.actionCode,
      resultCode: filters.resultCode,
      startAt: filters.startDate ? `${filters.startDate}T00:00:00` : '',
      endAt: filters.endDate ? `${filters.endDate}T23:59:59` : '',
    })}`),
}
