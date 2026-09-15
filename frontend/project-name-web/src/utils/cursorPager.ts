// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { useState } from 'react'

/**
 * 键集游标的「上一页 / 下一页」。
 *
 * 游标只能往前走——服务端给的是「从这里继续」，不是「第 N 页」。回退能力因此必须由调用方
 * 自己记住来路：visited 就是那份记忆。
 *
 * 筛选条件一变（resetKey 变），就回到第一页：旧游标锚在旧结果集上，带着它翻会得到一段
 * 无法解释的结果。重置是按 key 比较推导出来的，不靠 effect——少一次「先用旧游标请求一遍」的渲染。
 */
export type PagerState = {
  resetKey: string
  cursor: string | null
  visited: (string | null)[]
}

export const firstPage = (resetKey: string): PagerState => ({
  resetKey,
  cursor: null,
  visited: [],
})

/** 筛选条件没变原样返回；变了回到第一页。 */
export const pagerForKey = (state: PagerState, resetKey: string): PagerState =>
  state.resetKey === resetKey ? state : firstPage(resetKey)

export const nextPage = (state: PagerState, nextCursor: string): PagerState => ({
  ...state,
  cursor: nextCursor,
  visited: [...state.visited, state.cursor],
})

/** 已在第一页时原样返回——「上一页」按钮在那里本就不可用，这里不再造一个状态。 */
export const previousPage = (state: PagerState): PagerState => {
  if (state.visited.length === 0) return state
  return {
    ...state,
    cursor: state.visited[state.visited.length - 1],
    visited: state.visited.slice(0, -1),
  }
}

export function useCursorPager(resetKey = '') {
  const [stored, setStored] = useState(() => firstPage(resetKey))
  const state = pagerForKey(stored, resetKey)
  return {
    cursor: state.cursor,
    canGoPrevious: state.visited.length > 0,
    next: (nextCursor: string) => setStored(nextPage(state, nextCursor)),
    previous: () => setStored(previousPage(state)),
  }
}
