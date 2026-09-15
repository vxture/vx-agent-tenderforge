// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { firstPage, nextPage, pagerForKey, previousPage } from './cursorPager'

/**
 * 翻页记忆。
 *
 * 错了不会报错：回退丢一步会让用户「上一页」回到别的页，筛选变了不重置会带着旧游标
 * 去翻一个新结果集——两种表现都像「数据就是这样」。
 */
describe('cursorPager', () => {
  it('往前翻再逐页退回，每一步都回到走过的那个游标，最后回到第一页', () => {
    const page2 = nextPage(firstPage('k'), 'c2')
    const page3 = nextPage(page2, 'c3')

    expect(page3.cursor).toBe('c3')
    expect(previousPage(page3)).toEqual(page2)
    expect(previousPage(previousPage(page3))).toEqual(firstPage('k'))
  })

  it('已在第一页时「上一页」原样返回', () => {
    const start = firstPage('k')

    expect(previousPage(start)).toBe(start)
  })

  it('筛选条件变了回到第一页并清空来路，没变原样返回', () => {
    const deep = nextPage(nextPage(firstPage('TEMPLATE|'), 'c2'), 'c3')

    expect(pagerForKey(deep, 'TEMPLATE|')).toBe(deep)
    expect(pagerForKey(deep, 'OUTLINE|')).toEqual(firstPage('OUTLINE|'))
  })
})
