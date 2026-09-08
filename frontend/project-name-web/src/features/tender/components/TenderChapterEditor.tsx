// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useEffect, useState } from 'react'

import { TableKit } from '@tiptap/extension-table'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import {
  Button,
  Field,
  FieldLabel,
  Icon,
  type IconName,
  Input,
  Popover,
  PopoverContent,
  PopoverTrigger,
  Spinner,
} from '@vxture/design-system'

import { normalizeEditorTables, numberEditorTables } from './editorTableNormalization'
import { createTableBundle, TableNote, TableTitle } from './TableLabelsExtension'

import type { TableNumberingContext } from '../tableNumbering'

const MIN_TABLE_DIMENSION = 1
const MAX_TABLE_ROWS = 30
const MAX_TABLE_COLUMNS = 20

export function TenderChapterEditor({
  content,
  saving,
  tableNumbering,
  onSave,
}: {
  content: string
  saving: boolean
  tableNumbering: TableNumberingContext
  onSave: (content: string) => Promise<void>
}) {
  const [dirty, setDirty] = useState(false)
  const editor = useEditor({
    extensions: [
      StarterKit,
      TableKit.configure({ table: { resizable: true, allowTableNodeSelection: true } }),
      TableTitle,
      TableNote,
    ],
    content: prepareEditorContent(content, tableNumbering),
    immediatelyRender: false,
    editorProps: {
      attributes: {
        class: 'report-editor-content min-h-[680px]',
        'aria-label': '章节正文编辑器',
      },
    },
    onUpdate: () => setDirty(true),
  })

  useEffect(() => {
    if (!editor || dirty) return
    editor.commands.setContent(prepareEditorContent(content, tableNumbering), { emitUpdate: false })
  }, [content, dirty, editor, tableNumbering])

  useEffect(() => {
    setDirty(false)
  }, [content])

  if (!editor) return null

  const save = async () => {
    const normalized = normalizeEditorContent(editor.getHTML(), tableNumbering)
    await onSave(normalized)
    editor.commands.setContent(prepareEditorContent(normalized, tableNumbering), { emitUpdate: false })
    setDirty(false)
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col bg-surface-2">
      <div className="flex h-header-sm shrink-0 items-center justify-between gap-xs border-b border-border bg-card px-sm">
        <div className="flex min-w-0 items-center gap-2xs">
          <ToolButton
            label="撤销"
            active={false}
            disabled={!editor.can().chain().focus().undo().run()}
            onClick={() => editor.chain().focus().undo().run()}
            icon="undo"
          />
          <ToolButton
            label="重做"
            active={false}
            disabled={!editor.can().chain().focus().redo().run()}
            onClick={() => editor.chain().focus().redo().run()}
            icon="refresh"
          />
          <span className="mx-2xs h-control-xs w-px shrink-0 bg-border" />
          <TableInsertControl
            onInsert={(rows, columns) => {
              editor.chain().focus().insertContent(createTableBundle(rows, columns)).run()
            }}
          />
        </div>
        <Button className="shrink-0" size="sm" disabled={!dirty || saving} onClick={() => void save()}>
          {saving ? <Spinner size="sm" /> : <Icon name="save" size="sm" />}
          {saving ? '保存中' : dirty ? '保存正文' : '已保存'}
        </Button>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-sm sm:p-xl">
        <div className="report-editor-page mx-auto min-h-full w-full max-w-[900px] bg-card shadow-raised">
          <EditorContent editor={editor} />
        </div>
      </div>
    </div>
  )
}

function normalizeEditorContent(content: string, tableNumbering: TableNumberingContext) {
  const container = document.createElement('div')
  container.innerHTML = content
  container.querySelectorAll('[data-flow-diagram]').forEach((element) => element.remove())
  container.querySelectorAll('p').forEach((paragraph) => {
    if (paragraph.closest('table')) return
    const parsed = parseMarkdownTable(paragraph.textContent ?? '')
    if (parsed) {
      paragraph.replaceWith(createTableElements(parsed))
      return
    }
    splitNumberedParagraph(paragraph)
  })
  normalizeEditorTables(container, tableNumbering.fallbackTitle)
  numberEditorTables(container, tableNumbering)
  return container.innerHTML
}

/**
 * 将服务端表格标签转换为无歧义的 Tiptap 输入标签。
 *
 * @preconditions content 为服务端返回或编辑器序列化的正文 HTML。
 * @sideEffects 不修改服务端数据，仅构造本次编辑器加载使用的 HTML。
 * @errorHandling 缺失表题或表注时由 normalizeEditorTables 补齐后再转换。
 */
function prepareEditorContent(content: string, tableNumbering: TableNumberingContext) {
  const container = document.createElement('div')
  container.innerHTML = normalizeEditorContent(content, tableNumbering)
  replaceTableLabelTags(container, 'p[data-table-title]', 'tender-table-title')
  replaceTableLabelTags(container, 'p[data-table-note]', 'tender-table-note')
  return container.innerHTML
}

function replaceTableLabelTags(container: HTMLElement, selector: string, tagName: string) {
  container.querySelectorAll(selector).forEach((label) => {
    const editorLabel = document.createElement(tagName)
    editorLabel.innerHTML = label.innerHTML
    label.replaceWith(editorLabel)
  })
}

type ParsedMarkdownTable = {
  caption: string
  header: string[]
  rows: string[][]
}

function parseMarkdownTable(value: string): ParsedMarkdownTable | null {
  const text = value.trim()
  if (!text.includes('|')) return null
  return parseMultilineTable(text) ?? parseInlineTable(text)
}

function parseMultilineTable(text: string): ParsedMarkdownTable | null {
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  const rows = lines.map(splitPipeRow)
  for (let index = 1; index < rows.length; index += 1) {
    const separator = rows[index]
    const header = rows[index - 1]
    if (!separator || !header || !isSeparatorRow(separator) || header.length !== separator.length) {
      continue
    }
    const data = rows.slice(index + 1).filter((row): row is string[] => Boolean(row?.length === header.length))
    if (!data.length) continue
    return { caption: lines.slice(0, index - 1).join(' '), header, rows: data }
  }
  return null
}

function parseInlineTable(text: string): ParsedMarkdownTable | null {
  const rows = text
    .split(/\|\s*\|/)
    .map((chunk) => chunk.trim())
    .filter((chunk) => chunk.replaceAll('|', '').trim())
    .map(splitPipeRow)
  for (let index = 1; index < rows.length; index += 1) {
    const separator = rows[index]
    const sourceHeader = rows[index - 1]
    if (!separator || !sourceHeader || !isSeparatorRow(separator)) continue
    const header = [...sourceHeader]
    let caption = ''
    if (header.length === separator.length + 1 && /^表\s*[0-9一二三四五六七八九十]/.test(header[0])) {
      caption = header.shift() ?? ''
    } else if (
      header.length === separator.length + 1
      && /^表\s*[0-9一二三四五六七八九十]/.test(header.at(-1) ?? '')
    ) {
      caption = header.pop() ?? ''
    }
    if (header.length !== separator.length) continue
    const data = rows.slice(index + 1).filter((row): row is string[] => Boolean(row?.length === header.length))
    if (data.length) return { caption, header, rows: data }
  }
  return null
}

function splitPipeRow(value: string): string[] | null {
  const normalized = value.trim().replace(/^\|/, '').replace(/\|$/, '').trim()
  if (!normalized.includes('|')) return null
  const cells = normalized.split('|').map((cell) => cell.trim())
  return cells.length >= 2 ? cells : null
}

function isSeparatorRow(row: string[]) {
  return row.length >= 2 && row.every((cell) => /^:?-{3,}:?$/.test(cell))
}

function createTableElements(tableData: ParsedMarkdownTable) {
  const fragment = document.createDocumentFragment()
  if (tableData.caption) {
    const title = document.createElement('p')
    title.dataset.tableTitle = 'true'
    title.textContent = tableData.caption
    fragment.append(title)
  }
  const table = document.createElement('table')
  const head = table.createTHead()
  const headerRow = head.insertRow()
  tableData.header.forEach((value) => {
    const cell = document.createElement('th')
    cell.textContent = value
    headerRow.append(cell)
  })
  const body = table.createTBody()
  tableData.rows.forEach((values) => {
    const row = body.insertRow()
    values.forEach((value) => {
      const cell = row.insertCell()
      cell.textContent = value
    })
  })
  fragment.append(table)
  const note = document.createElement('p')
  note.dataset.tableNote = 'true'
  fragment.append(note)
  return fragment
}

function splitNumberedParagraph(paragraph: HTMLParagraphElement) {
  if (paragraph.childElementCount) return
  const value = paragraph.textContent ?? ''
  const pattern = /(^|\s+|[。！？；])(?:(\d{1,3})[.．]\s+|[（(](\d{1,3})[）)]\s*|(\d{1,3})[）)]\s*|(第[一二三四五六七八九十百]+)[，、]\s*)(?=\S)/g
  const matches = [...value.matchAll(pattern)]
  if (!matches.length) return
  const starts = matches.map((match) => (match.index ?? 0) + match[1].length)
  if (starts.length === 1 && starts[0] > 0 && !matches[0][5]) return
  const parts: string[] = []
  const prefix = value.slice(0, starts[0]).trim()
  if (prefix) parts.push(prefix)
  matches.forEach((match, index) => {
    const end = index + 1 < starts.length ? starts[index + 1] : value.length
    const markerEnd = (match.index ?? 0) + match[0].length
    const number = match[2] ?? match[3] ?? match[4]
    const marker = match[5] ? `${match[5]}，` : match[2] ? `${number}. ` : `（${number}）`
    parts.push(`${marker}${value.slice(markerEnd, end).trim()}`)
  })
  if (parts.length === 1 && parts[0] === value) return
  const fragment = document.createDocumentFragment()
  parts.filter(Boolean).forEach((part) => {
    const item = document.createElement('p')
    item.textContent = part
    fragment.append(item)
  })
  paragraph.replaceWith(fragment)
}

function TableInsertControl({ onInsert }: { onInsert: (rows: number, columns: number) => void }) {
  const [open, setOpen] = useState(false)
  const [rows, setRows] = useState(3)
  const [columns, setColumns] = useState(3)

  const insert = () => {
    const safeRows = clampTableDimension(rows, MAX_TABLE_ROWS)
    const safeColumns = clampTableDimension(columns, MAX_TABLE_COLUMNS)
    setRows(safeRows)
    setColumns(safeColumns)
    onInsert(safeRows, safeColumns)
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="sm">
          <Icon name="table" size="sm" />
          插入表格
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-panel-sm">
        <form
          className="flex flex-col gap-sm"
          aria-label="设置插入表格的行列数"
          onSubmit={(event) => {
            event.preventDefault()
            insert()
          }}
        >
          <div className="text-title-sm">插入表格</div>
          <div className="grid grid-cols-2 gap-sm">
            <TableDimensionInput
              label="行数"
              value={rows}
              maximum={MAX_TABLE_ROWS}
              onChange={setRows}
            />
            <TableDimensionInput
              label="列数"
              value={columns}
              maximum={MAX_TABLE_COLUMNS}
              onChange={setColumns}
            />
          </div>
          <p className="text-body-sm text-muted-foreground">
            将插入 {rows} 行 × {columns} 列，并自动添加表题和表注。
          </p>
          <div className="flex justify-end gap-xs">
            <Button type="button" variant="outline" size="sm" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button type="submit" size="sm">插入</Button>
          </div>
        </form>
      </PopoverContent>
    </Popover>
  )
}

function TableDimensionInput({
  label,
  value,
  maximum,
  onChange,
}: {
  label: string
  value: number
  maximum: number
  onChange: (value: number) => void
}) {
  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      <Input
        type="number"
        min={MIN_TABLE_DIMENSION}
        max={maximum}
        value={value}
        onChange={(event) => onChange(clampTableDimension(Number(event.target.value), maximum))}
      />
    </Field>
  )
}

function clampTableDimension(value: number, maximum: number) {
  if (!Number.isFinite(value)) return MIN_TABLE_DIMENSION
  return Math.min(maximum, Math.max(MIN_TABLE_DIMENSION, Math.trunc(value)))
}

function ToolButton({
  label,
  active,
  disabled,
  onClick,
  icon,
}: {
  label: string
  active: boolean
  disabled?: boolean
  onClick: () => void
  icon: IconName
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      disabled={disabled}
      aria-pressed={active}
      aria-label={label}
      title={label}
      className={active ? 'bg-surface-selected text-primary-text' : ''}
      onClick={onClick}
    >
      <Icon name={icon} size="sm" />
    </Button>
  )
}
