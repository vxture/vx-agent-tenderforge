// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-11

import { formatTableTitle, type TableNumberingContext } from '../tableNumbering'

const TABLE_TITLE_PATTERN = /^(?:表(?:格)?\s*[0-9一二三四五六七八九十百]+|表题\s*[：:])/i
const TABLE_NOTE_PATTERN = /^(?:表注|注)\s*[：:]/i
const TABLE_TITLE_SUFFIXES = ['对照表', '明细表', '一览表', '汇总表', '配置表', '矩阵', '清单']

/**
 * 将历史正文及模型 HTML 表格修复为 Tiptap 的表题、表格、表注三段结构。
 *
 * @preconditions container 已装载经过接口层清理的受限正文 HTML。
 * @sideEffects 原地调整 container 内的表格节点，不修改服务端数据。
 * @errorHandling 无法可靠判断异常单元格时保留原表格，并补充可编辑的兜底表题。
 */
export function normalizeEditorTables(container: HTMLElement, fallbackContext = '') {
  const tables = Array.from(container.querySelectorAll('table'))
  tables.forEach((table, index) => normalizeTable(table, fallbackContext, index + 1))
}

/**
 * 按一级章节为编辑器内所有表题生成确定性编号。
 *
 * @preconditions normalizeEditorTables 已保证每张表前存在 data-table-title 段落。
 * @sideEffects 原地改写表题文本，不修改表格数据和表注。
 * @errorHandling 表题为空时使用章节标题或表头生成的兜底语义名称。
 */
export function numberEditorTables(
  container: HTMLElement,
  context: TableNumberingContext,
) {
  Array.from(container.querySelectorAll('table')).forEach((table, index) => {
    const title = table.previousElementSibling
    if (!isTitleParagraph(title)) return
    const fallbackTitle = buildFallbackTitle(table, context.fallbackTitle, index + 1)
    title.textContent = formatTableTitle(context, index, title.textContent ?? '', fallbackTitle)
  })
}

function normalizeTable(table: HTMLTableElement, fallbackContext: string, tableIndex: number) {
  const embeddedCaption = cleanText(table.querySelector('caption')?.textContent ?? '')
  table.querySelectorAll('caption').forEach((caption) => caption.remove())
  const titleNodes = precedingTitleParagraphs(table)
  let titleNode = titleNodes[0] ?? null
  titleNodes.slice(1).forEach((duplicate) => duplicate.remove())
  const rowTitle = repairTitleCell(table, embeddedCaption)
  const title = cleanTitle(titleNode?.textContent || embeddedCaption || rowTitle)
    || buildFallbackTitle(table, fallbackContext, tableIndex)
  if (!titleNode) {
    titleNode = document.createElement('p')
    table.before(titleNode)
  }
  titleNode.dataset.tableTitle = 'true'
  titleNode.textContent = title
  ensureTableNote(table)
}

function precedingTitleParagraphs(table: HTMLTableElement) {
  const titles: HTMLParagraphElement[] = []
  let preceding = table.previousElementSibling
  while (isTitleParagraph(preceding) || isPlainTitleParagraph(preceding)) {
    titles.unshift(preceding)
    preceding = preceding.previousElementSibling
  }
  return titles
}

function repairTitleCell(table: HTMLTableElement, knownCaption: string) {
  const rows = Array.from(table.rows)
  const firstRow = rows[0]
  if (!firstRow || rows.length < 2) return ''
  const cells = Array.from(firstRow.cells)
  const expectedColumns = mostCommonPositive(rows.slice(1).map((row) => row.cells.length))
  if (cells.length === 1 && titleCellScore(cells[0], knownCaption) >= 3) {
    const title = cleanText(cells[0].textContent ?? '')
    table.deleteRow(0)
    return title
  }
  if (!expectedColumns || cells.length !== expectedColumns + 1) return ''
  const candidates = [cells[0], cells[cells.length - 1]]
    .map((cell) => ({ cell, score: titleCellScore(cell, knownCaption) }))
    .sort((left, right) => right.score - left.score)
  if (candidates[0].score < 2 || candidates[0].score === candidates[1].score) return ''
  const title = cleanText(candidates[0].cell.textContent ?? '')
  firstRow.deleteCell(candidates[0].cell.cellIndex)
  return title
}

function titleCellScore(cell: HTMLTableCellElement, knownCaption: string) {
  const text = cleanText(cell.textContent ?? '')
  let score = 0
  if (knownCaption && text === knownCaption) score += 12
  if (TABLE_TITLE_PATTERN.test(text)) score += 10
  if (cell.hasAttribute('colspan')) score += 8
  if (TABLE_TITLE_SUFFIXES.some((suffix) => text.endsWith(suffix))) score += 4
  if (text.length >= 10) score += 2
  return score
}

function ensureTableNote(table: HTMLTableElement) {
  const following = table.nextElementSibling
  if (isNoteParagraph(following)) {
    following.dataset.tableNote = 'true'
    return
  }
  const note = document.createElement('p')
  note.dataset.tableNote = 'true'
  table.after(note)
}

function buildFallbackTitle(table: HTMLTableElement, fallbackContext: string, tableIndex: number) {
  const context = cleanText(fallbackContext)
  if (context) return `${context}明细表${tableIndex > 1 ? `（${tableIndex}）` : ''}`
  const headers = Array.from(table.rows[0]?.cells ?? [])
    .map((cell) => cleanText(cell.textContent ?? ''))
    .filter(Boolean)
  if (headers.length >= 2) return `${headers[0]}与${headers[1]}对照表`
  if (headers.length === 1) return `${headers[0]}明细表`
  return `技术内容明细表${tableIndex > 1 ? `（${tableIndex}）` : ''}`
}

function isTitleParagraph(element: Element | null): element is HTMLParagraphElement {
  return element instanceof HTMLParagraphElement && element.hasAttribute('data-table-title')
}

function isPlainTitleParagraph(element: Element | null): element is HTMLParagraphElement {
  if (!(element instanceof HTMLParagraphElement)) return false
  const text = cleanText(element.textContent ?? '')
  return TABLE_TITLE_PATTERN.test(text)
    || TABLE_TITLE_SUFFIXES.some((suffix) => text.endsWith(suffix))
}

function isNoteParagraph(element: Element | null): element is HTMLParagraphElement {
  return element instanceof HTMLParagraphElement
    && (element.hasAttribute('data-table-note') || TABLE_NOTE_PATTERN.test(cleanText(element.textContent ?? '')))
}

function mostCommonPositive(values: number[]) {
  const counts = new Map<number, number>()
  values.filter((value) => value > 0).forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1))
  return [...counts.entries()].sort((left, right) => right[1] - left[1])[0]?.[0] ?? 0
}

function cleanText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

function cleanTitle(value: string) {
  return cleanText(value).replace(/^表题\s*[：:]\s*/, '')
}
