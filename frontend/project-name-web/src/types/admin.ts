// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
/**
 * 无界流水的响应形状（产品接入通则 A-3 / A-4）。
 *
 * 没有 total 与 totalPages 是刻意的：审计表因为系统自己跑而增长，
 * 一个总数在返回给界面的那一刻就已经过时，而它的代价是一次全表计数。
 * 「还能不能继续翻」由 nextCursor 回答，这是唯一不会自相矛盾的答案。
 */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
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
  limit: number
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

/**
 * 审计条目，字段名取自《产品接入通则》X-3 的最小字段集。
 *
 * 这些名字不是本仓的偏好，是跨产品对账的前提——三个产品各写各的名字，
 * 「都合规」与「能一起查」就不是一回事了。
 *
 * `actorName` / `objectName` 是服务端 join 出来的展示字段，不属于最小集，
 * 不要拿它们做任何判断：被引用对象一改名它们就变了。
 */
export interface AuditLogEntry {
  eventId: string
  actorId: string | null
  actorName: string | null
  /** 发起动作的控制台 RP；后台通道为 null，不要在这里兜底成产品名。 */
  actorConsole: string | null
  objectName: string | null
  action: string
  objectType: string
  objectId: string | null
  outcome: string
  detailSummary: string | null
  /** 跨产品聚合键（X-2）。调用方没送时为 null。 */
  taskId: string | null
  orgId: string | null
  workspaceId: string | null
  traceId: string
  ipAddress: string | null
  occurredAt: string
}

export interface AuditLogFilters {
  limit: number
  /** 服务端铸的不透明游标；null 表示第一页。不要自己构造它。 */
  cursor: string | null
  keyword: string
  actionCode: string
  resultCode: string
  startDate: string
  endDate: string
}
