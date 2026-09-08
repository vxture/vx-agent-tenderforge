// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
import { type JSONContent, mergeAttributes, Node } from '@tiptap/core'

function tableLabelNode(
  name: string,
  attribute: string,
  inputTag: string,
  placeholder: string,
) {
  return Node.create({
    name,
    priority: 1_000,
    group: 'block',
    content: 'inline*',
    selectable: false,
    parseHTML() {
      return [
        { tag: inputTag, priority: 1_000 },
        { tag: `p[${attribute}="true"]`, priority: 999 },
      ]
    },
    renderHTML({ HTMLAttributes }) {
      return [
        'p',
        mergeAttributes(HTMLAttributes, {
          [attribute]: 'true',
          'data-placeholder': placeholder,
        }),
        0,
      ]
    },
  })
}

export const TableTitle = tableLabelNode(
  'tableTitle',
  'data-table-title',
  'tender-table-title',
  '输入表题',
)
export const TableNote = tableLabelNode(
  'tableNote',
  'data-table-note',
  'tender-table-note',
  '输入表注（可留空）',
)

export function createTableBundle(rows = 3, columns = 3): JSONContent[] {
  return [
    { type: 'tableTitle' },
    {
      type: 'table',
      content: Array.from({ length: rows }, (_, rowIndex) => ({
        type: 'tableRow',
        content: Array.from({ length: columns }, () => ({
          type: rowIndex === 0 ? 'tableHeader' : 'tableCell',
          content: [{ type: 'paragraph' }],
        })),
      })),
    },
    { type: 'tableNote' },
  ]
}
