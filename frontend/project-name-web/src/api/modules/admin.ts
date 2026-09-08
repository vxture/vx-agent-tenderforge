// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { apiRequest } from '@/api/client'
import type {
  AuditLogEntry,
  AuditLogFilters,
  CreateManagedUserInput,
  ManagedUser,
  ManagedUserFilters,
  PageResult,
  UpdateManagedUserInput,
} from '@/types/admin'

const query = (values: Record<string, string | number>) => {
  const result = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (String(value).trim()) result.set(key, String(value))
  })
  return result.toString()
}

export const adminApi = {
  listUsers: (filters: ManagedUserFilters) =>
    apiRequest<PageResult<ManagedUser>>(`/api/admin/users?${query({
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword,
      roleCode: filters.roleCode,
      enabled: filters.enabled,
    })}`),
  createUser: (input: CreateManagedUserInput) =>
    apiRequest<ManagedUser>('/api/admin/users', { method: 'POST', body: input }),
  updateUser: (userId: string, input: UpdateManagedUserInput) =>
    apiRequest<ManagedUser>(`/api/admin/users/${userId}`, { method: 'PATCH', body: input }),
  deactivateUser: (userId: string) =>
    apiRequest<ManagedUser>(`/api/admin/users/${userId}`, { method: 'DELETE' }),
  listAuditLogs: (filters: AuditLogFilters) => {
    const values: Record<string, string | number> = {
      page: filters.page,
      size: filters.size,
      keyword: filters.keyword,
      actionCode: filters.actionCode,
      resultCode: filters.resultCode,
      startAt: filters.startDate ? `${filters.startDate}T00:00:00` : '',
      endAt: filters.endDate ? `${filters.endDate}T23:59:59` : '',
    }
    return apiRequest<PageResult<AuditLogEntry>>(`/api/admin/audit-logs?${query(values)}`)
  },
}
