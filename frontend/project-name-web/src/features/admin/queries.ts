// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { useQuery } from '@tanstack/react-query'

import { adminApi } from '@/api/modules/admin'
import type { AuditLogFilters } from '@/types/admin'

const adminKeys = {
  auditLogs: (filters: AuditLogFilters) => ['admin-audit-logs', filters] as const,
}

export const useAuditLogsQuery = (filters: AuditLogFilters) =>
  useQuery({
    queryKey: adminKeys.auditLogs(filters),
    queryFn: () => adminApi.listAuditLogs(filters),
  })
