// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import type { BidOutlineNode } from '@/types/tender'

import { buildOutlineLabels } from './outlineNumbering'

const node = (
  id: string,
  parentId: string | null,
  level: number,
  sortOrder: number,
  title = id
): BidOutlineNode =>
  ({
    id,
    parentId,
    level,
    title,
    plannedPages: 0,
    sortOrder,
    revision: 0,
    taskBrief: '',
    mustKeywords: [],
    scoringPointIds: [],
  }) as unknown as BidOutlineNode

describe('buildOutlineLabels', () => {
  it('三级目录分别编成「第一章」「一、」「（一）」', () => {
    const labels = buildOutlineLabels([
      node('r1', null, 1, 0, '技术方案'),
      node('s1', 'r1', 2, 0, '总体设计'),
      node('l1', 's1', 3, 0, '架构说明'),
    ])

    expect(labels.get('r1')).toBe('第一章 技术方案')
    expect(labels.get('s1')).toBe('一、总体设计')
    expect(labels.get('l1')).toBe('（一）架构说明')
  })

  it('同级按 sortOrder 编号，sortOrder 相同时按标识，编号在每个父节点下重新开始', () => {
    const labels = buildOutlineLabels([
      node('r-b', null, 1, 1, '实施计划'),
      node('r-a', null, 1, 0, '技术方案'),
      node('s-y', 'r-a', 2, 5, '乙'),
      node('s-x', 'r-a', 2, 5, '甲'),
      node('s-z', 'r-b', 2, 0, '丙'),
    ])

    expect(labels.get('r-a')).toBe('第一章 技术方案')
    expect(labels.get('r-b')).toBe('第二章 实施计划')
    expect(labels.get('s-x')).toBe('一、甲')
    expect(labels.get('s-y')).toBe('二、乙')
    expect(labels.get('s-z')).toBe('一、丙')
  })

  it('标题里模型或用户写过的旧编号被去掉，不叠成「第一章 第三章」', () => {
    const labels = buildOutlineLabels([
      node('r1', null, 1, 0, ' 第三章 技术方案 '),
      node('s1', 'r1', 2, 0, '2、实施计划'),
      node('l1', 's1', 3, 0, '（5）质量保障'),
    ])

    expect(labels.get('r1')).toBe('第一章 技术方案')
    expect(labels.get('s1')).toBe('一、实施计划')
    expect(labels.get('l1')).toBe('（一）质量保障')
  })

  it('十以上用中文数字：十、十一、二十、二十一、一百零五', () => {
    const siblings = Array.from({ length: 105 }, (_, index) =>
      node(`s${String(index + 1).padStart(3, '0')}`, 'r1', 2, index, `条目${index + 1}`)
    )
    const labels = buildOutlineLabels([node('r1', null, 1, 0, '附录'), ...siblings])

    expect(labels.get('s010')).toBe('十、条目10')
    expect(labels.get('s011')).toBe('十一、条目11')
    expect(labels.get('s020')).toBe('二十、条目20')
    expect(labels.get('s021')).toBe('二十一、条目21')
    expect(labels.get('s105')).toBe('一百零五、条目105')
  })
})
