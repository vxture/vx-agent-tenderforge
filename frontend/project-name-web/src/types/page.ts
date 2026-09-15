// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
/**
 * 游标分页的响应形状（产品接入通则 A-3 / A-4）。
 *
 * 没有 total 与 totalPages 是刻意的：列表会增长，一个总数在返回给界面的那一刻就已经过时，
 * 而它的代价是一次全表计数。「还能不能继续翻」由 nextCursor 回答，这是唯一不会自相矛盾的答案。
 * nextCursor 是服务端铸的不透明串：原样送回，不要解析、截断或自己构造它（A-5）。
 */
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}

/** 列表请求的分页参数。cursor 为 null 或缺省表示第一页；limit 缺省由服务端定，上限 200。 */
export type PageRequest = {
  cursor?: string | null
  limit?: number
}
