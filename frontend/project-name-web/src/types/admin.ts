// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
/** 游标分页形状移到 types/page，标书、素材、导出列表与审计共用同一个定义。 */
export type { CursorPage } from './page'

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
