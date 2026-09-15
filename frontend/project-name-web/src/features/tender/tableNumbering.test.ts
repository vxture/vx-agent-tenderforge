// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { chapterSummary as chapter, outlineNode as node } from '@/test/fixtures/tender'

import { buildTableNumberingContext, formatTableTitle, stripTableNumberPrefix } from './tableNumbering'

/** 两个一级章节：r1 下两个叶子（各 2、3 张表），r2 下一个叶子（1 张表）。 */
const outline = [
  node('r1', null, 1, 0),
  node('s1', 'r1', 2, 1),
  node('l1', 's1', 3, 2),
  node('l2', 's1', 3, 3),
  node('r2', null, 1, 4),
  node('s2', 'r2', 2, 5),
  node('l3', 's2', 3, 6),
]
const chapters = [chapter('c1', 'l1', 2), chapter('c2', 'l2', 3), chapter('c3', 'l3', 1)]

describe('buildTableNumberingContext', () => {
  it('表号在同一个一级章节内跨叶子章节连续', () => {
    expect(buildTableNumberingContext(outline, chapters, 'c2')).toEqual({
      chapterNumber: 1,
      tableStart: 3,
      fallbackTitle: 'l2',
    })
  })

  it('进入下一个一级章节时表号从 1 重新开始，前一章的表不计入', () => {
    expect(buildTableNumberingContext(outline, chapters, 'c3')).toEqual({
      chapterNumber: 2,
      tableStart: 1,
      fallbackTitle: 'l3',
    })
  })

  it('一级章节按 sortOrder 排号，与数组顺序无关', () => {
    const shuffled = [...outline].reverse()
    expect(buildTableNumberingContext(shuffled, chapters, 'c3').chapterNumber).toBe(2)
  })

  it('负数表数量按 0 计，不把后面的表号拉到 0 或负数', () => {
    const broken = [chapter('c1', 'l1', -4), chapter('c2', 'l2', 3)]
    expect(buildTableNumberingContext(outline, broken, 'c2').tableStart).toBe(1)
  })

  it('目录暂时不完整、找不到当前章节时回退为第一章第一张表，不阻断正文查看', () => {
    expect(buildTableNumberingContext(outline, chapters, 'missing')).toEqual({
      chapterNumber: 1,
      tableStart: 1,
      fallbackTitle: '',
    })
  })
})

describe('表题编号', () => {
  it('去掉历史编号的各种写法', () => {
    expect(stripTableNumberPrefix('表1 资源配置')).toBe('资源配置')
    expect(stripTableNumberPrefix('表 9-3：实施分工')).toBe('实施分工')
    expect(stripTableNumberPrefix('表一 运行保障')).toBe('运行保障')
    expect(stripTableNumberPrefix('表格 2.1、设备清单')).toBe('设备清单')
    expect(stripTableNumberPrefix('表题：  人员  配置 ')).toBe('人员 配置')
  })

  it('用新编号替换旧编号而不是叠加，语义标题为空时用兜底标题', () => {
    const context = { chapterNumber: 2, tableStart: 4, fallbackTitle: '验收方法' }

    expect(formatTableTitle(context, 0, '表 1-1 验收准则', '')).toBe('表 2-4 验收准则')
    expect(formatTableTitle(context, 1, '', '验收方法明细表')).toBe('表 2-5 验收方法明细表')
  })

  it('语义标题与兜底标题都空时给一个通用名，而不是只剩编号', () => {
    const context = { chapterNumber: 1, tableStart: 1, fallbackTitle: '' }
    expect(formatTableTitle(context, 2, '  ', '表 3')).toBe('表 1-3 技术内容明细表')
  })
})
