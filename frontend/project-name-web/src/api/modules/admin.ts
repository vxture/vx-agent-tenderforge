// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
import { apiRequest } from '@/api/client'
import type {
  AuditLogEntry,
  AuditLogFilters,
  CreateManagedUserInput,
  CursorPage,
  ManagedUser,
  ManagedUserFilters,
  UpdateManagedUserInput,
} from '@/types/admin'

const query = (values: Record<string, string | number | null>) => {
  const result = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && String(value).trim()) result.set(key, String(value))
  })
  return result.toString()
}

export const adminApi = {
  /** 有界管理面对象，服务端返回裸数组（通则 A-4）。 */
  listUsers: (filters: ManagedUserFilters) =>
    apiRequest<ManagedUser[]>(`/api/admin/users?${query({
      limit: filters.limit,
      keyword: filters.keyword,
      roleCode: filters.roleCode,
      enabled: filters.enabled,
    })}`),
  createUser: (input: CreateManagedUserInput) =>
    apiRequest<ManagedUser>('/api/admin/users', { method: 'POST', body: input }),
  updateUser: (userId: string, input: UpdateManagedUserInput) =>
    apiRequest<ManagedUser>(`/api/admin/users/${userId}`, { method: 'PATCH', body: input }),
  // 停用 / 启用是状态迁移，走成对的具名路由（通则 B-3、B-4）。
  deactivateUser: (userId: string) =>
    apiRequest<ManagedUser>(`/api/admin/users/${userId}/deactivate`, { method: 'POST' }),
  activateUser: (userId: string) =>
    apiRequest<ManagedUser>(`/api/admin/users/${userId}/activate`, { method: 'POST' }),
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
