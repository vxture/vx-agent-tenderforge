// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { BidChapterSummary, BidOutlineNode } from '@/types/tender'

/**
 * 目录与章节摘要的测试夹具。
 *
 * 只有编号、排序与表号计算真正读到的字段有意义，其余给中性值。集中在这里而不是每个测试文件各抄一份：
 * 抄的那几份迟早会在字段增减时走样，而且 SonarCloud 把它们算作重复代码（PR #45 首轮质量门禁正是红在这里）。
 */
export const outlineNode = (
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

export const chapterSummary = (id: string, outlineNodeId: string, tableCount: number): BidChapterSummary =>
  ({
    id,
    outlineNodeId,
    title: outlineNodeId,
    generationStatus: 'READY',
    tableCount,
    updatedAt: '2026-08-28T00:00:00+08:00',
    revision: 0,
  }) as unknown as BidChapterSummary
