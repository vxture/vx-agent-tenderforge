// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildTableNumberingContext,
  formatTableTitle,
  stripTableNumberPrefix,
} from '../.test-dist/features/tender/tableNumbering.js'

const outline = [
  node('r1', null, 1, 0),
  node('s1', 'r1', 2, 1),
  node('l1', 's1', 3, 2),
  node('l2', 's1', 3, 3),
  node('r2', null, 1, 4),
  node('s2', 'r2', 2, 5),
  node('l3', 's2', 3, 6),
]

const chapters = [
  chapter('c1', 'l1', 2),
  chapter('c2', 'l2', 3),
  chapter('c3', 'l3', 1),
]

test('table numbering continues across leaf chapters and resets at a top-level chapter', () => {
  assert.deepEqual(buildTableNumberingContext(outline, chapters, 'c2'), {
    chapterNumber: 1,
    tableStart: 3,
    fallbackTitle: 'l2',
  })
  assert.deepEqual(buildTableNumberingContext(outline, chapters, 'c3'), {
    chapterNumber: 2,
    tableStart: 1,
    fallbackTitle: 'l3',
  })
})

test('table titles replace legacy numbering without duplicating the prefix', () => {
  const context = { chapterNumber: 2, tableStart: 4, fallbackTitle: '验收方法' }

  assert.equal(stripTableNumberPrefix('表1 资源配置'), '资源配置')
  assert.equal(stripTableNumberPrefix('表 9-3：实施分工'), '实施分工')
  assert.equal(stripTableNumberPrefix('表一 运行保障'), '运行保障')
  assert.equal(formatTableTitle(context, 0, '表 1-1 验收准则', ''), '表 2-4 验收准则')
  assert.equal(formatTableTitle(context, 1, '', '验收方法明细表'), '表 2-5 验收方法明细表')
})

function node(id, parentId, level, sortOrder) {
  return {
    id,
    parentId,
    level,
    title: id,
    plannedPages: 0,
    sortOrder,
    revision: 0,
    taskBrief: '',
    mustKeywords: [],
    scoringPointIds: [],
  }
}

function chapter(id, outlineNodeId, tableCount) {
  return {
    id,
    outlineNodeId,
    title: outlineNodeId,
    generationStatus: 'READY',
    tableCount,
    updatedAt: '2026-08-28T00:00:00',
    revision: 0,
  }
}
