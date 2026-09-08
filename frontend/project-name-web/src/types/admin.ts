// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface ManagedUser {
  id: string
  username: string
  displayName: string
  roleCode: 'ADMIN' | 'PLANNER'
  avatarUrl: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
  revision: number
}

export interface ManagedUserFilters {
  page: number
  size: number
  keyword: string
  roleCode: '' | ManagedUser['roleCode']
  enabled: '' | 'true' | 'false'
}

export interface CreateManagedUserInput {
  username: string
  displayName: string
  roleCode: ManagedUser['roleCode']
  password: string
}

export interface UpdateManagedUserInput {
  displayName: string
  roleCode: ManagedUser['roleCode']
  enabled: boolean
  password?: string
  revision: number
}

export interface AuditLogEntry {
  id: string
  username: string | null
  targetName: string | null
  actionCode: string
  targetType: string
  resultCode: string
  detailSummary: string | null
  traceId: string
  ipAddress: string | null
  createdAt: string
}

export interface AuditLogFilters {
  page: number
  size: number
  keyword: string
  actionCode: string
  resultCode: string
  startDate: string
  endDate: string
}
