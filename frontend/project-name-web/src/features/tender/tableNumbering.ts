// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-28
import type { BidChapterSummary, BidOutlineNode } from '@/types/tender'

const TABLE_NUMBER_PREFIX =
  /^表(?:格)?\s*[0-9〇零一二三四五六七八九十百千两]+(?:\s*[-—.．]\s*[0-9〇零一二三四五六七八九十百千两]+)?\s*[-—、:：.．]?\s*/i

export type TableNumberingContext = {
  chapterNumber: number
  tableStart: number
  fallbackTitle: string
}

/**
 * 计算叶子章节在所属一级章节中的确定性表号起点。
 *
 * @preconditions outline 和 chapters 来自同一次目录摘要查询。
 * @sideEffects 无。
 * @errorHandling 目录暂时不完整时回退为第一章、第一张表，不阻断正文查看。
 */
export function buildTableNumberingContext(
  outline: BidOutlineNode[],
  chapters: BidChapterSummary[],
  activeChapterId: string,
): TableNumberingContext {
  const activeChapter = chapters.find((chapter) => chapter.id === activeChapterId)
  if (!activeChapter) return { chapterNumber: 1, tableStart: 1, fallbackTitle: '' }
  const nodeById = new Map(outline.map((node) => [node.id, node]))
  const activeRoot = findRoot(nodeById.get(activeChapter.outlineNodeId), nodeById)
  const roots = outline
    .filter((node) => node.level === 1)
    .sort(compareOutlineNodes)
  const chapterNumber = Math.max(1, roots.findIndex((node) => node.id === activeRoot?.id) + 1)
  const precedingTables = tablesBeforeActiveChapter(
    orderOutline(outline),
    chapters,
    nodeById,
    activeRoot?.id,
    activeChapter.id,
  )
  return {
    chapterNumber,
    tableStart: precedingTables + 1,
    fallbackTitle: activeChapter.title,
  }
}

export function formatTableTitle(
  context: TableNumberingContext,
  tableOffset: number,
  semanticTitle: string,
  fallbackTitle: string,
) {
  const title = stripTableNumberPrefix(semanticTitle) || stripTableNumberPrefix(fallbackTitle)
    || '技术内容明细表'
  return `表 ${context.chapterNumber}-${context.tableStart + tableOffset} ${title}`
}

export function stripTableNumberPrefix(value: string) {
  return value
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^表题\s*[：:]\s*/, '')
    .replace(TABLE_NUMBER_PREFIX, '')
    .trim()
}

function tablesBeforeActiveChapter(
  orderedNodes: BidOutlineNode[],
  chapters: BidChapterSummary[],
  nodeById: Map<string, BidOutlineNode>,
  activeRootId: string | undefined,
  activeChapterId: string,
) {
  const chapterByNode = new Map(chapters.map((chapter) => [chapter.outlineNodeId, chapter]))
  let tableCount = 0
  for (const node of orderedNodes) {
    const chapter = chapterByNode.get(node.id)
    if (!chapter) continue
    if (chapter.id === activeChapterId) return tableCount
    if (findRoot(node, nodeById)?.id === activeRootId) {
      tableCount += Math.max(0, chapter.tableCount)
    }
  }
  return 0
}

function orderOutline(outline: BidOutlineNode[]) {
  const children = new Map<string | null, BidOutlineNode[]>()
  outline.forEach((node) => {
    children.set(node.parentId, [...(children.get(node.parentId) ?? []), node])
  })
  children.forEach((nodes) => nodes.sort(compareOutlineNodes))
  const ordered: BidOutlineNode[] = []
  const visited = new Set<string>()
  const visit = (node: BidOutlineNode) => {
    if (visited.has(node.id)) return
    visited.add(node.id)
    ordered.push(node)
    children.get(node.id)?.forEach(visit)
  }
  children.get(null)?.forEach(visit)
  outline.slice().sort(compareOutlineNodes).forEach(visit)
  return ordered
}

function findRoot(
  node: BidOutlineNode | undefined,
  nodeById: Map<string, BidOutlineNode>,
) {
  let current = node
  const visited = new Set<string>()
  while (current?.parentId && !visited.has(current.id)) {
    visited.add(current.id)
    current = nodeById.get(current.parentId)
  }
  return current?.level === 1 ? current : undefined
}

function compareOutlineNodes(left: BidOutlineNode, right: BidOutlineNode) {
  return left.sortOrder - right.sortOrder || left.id.localeCompare(right.id)
}
