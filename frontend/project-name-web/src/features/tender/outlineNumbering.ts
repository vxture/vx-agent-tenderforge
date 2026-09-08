// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
import type { BidOutlineNode } from '@/types/tender'

const NUMBER_PREFIXES: Record<number, RegExp> = {
  1: /^第[〇零一二三四五六七八九十百千两\d]+章[、.．\s]*/,
  2: /^[〇零一二三四五六七八九十百千两\d]+[、.．]\s*/,
  3: /^[（(][〇零一二三四五六七八九十百千两\d]+[）)]\s*/,
}

export function buildOutlineLabels(outline: BidOutlineNode[]) {
  const siblings = new Map<string, BidOutlineNode[]>()
  outline.forEach((node) => {
    const key = node.parentId ?? '__root__'
    siblings.set(key, [...(siblings.get(key) ?? []), node])
  })
  const indexes = new Map<string, number>()
  siblings.forEach((nodes) => {
    nodes
      .sort((left, right) => left.sortOrder - right.sortOrder || left.id.localeCompare(right.id))
      .forEach((node, index) => indexes.set(node.id, index + 1))
  })
  return new Map(
    outline.map((node) => [
      node.id,
      `${outlinePrefix(node.level, indexes.get(node.id) ?? 1)}${stripOutlinePrefix(node)}`,
    ])
  )
}

function outlinePrefix(level: number, index: number) {
  const number = chineseNumber(index)
  if (level === 1) return `第${number}章 `
  if (level === 2) return `${number}、`
  return `（${number}）`
}

function stripOutlinePrefix(node: BidOutlineNode) {
  return node.title.trim().replace(NUMBER_PREFIXES[node.level] ?? /^$/, '').trim()
}

function chineseNumber(value: number): string {
  const digits = '零一二三四五六七八九'
  if (value < 10) return digits[value]
  if (value < 20) return `十${value % 10 ? digits[value % 10] : ''}`
  if (value < 100) return `${digits[Math.floor(value / 10)]}十${value % 10 ? digits[value % 10] : ''}`
  if (value < 1000) {
    const remainder = value % 100
    return `${digits[Math.floor(value / 100)]}百${remainder ? `${remainder < 10 ? '零' : ''}${chineseNumber(remainder)}` : ''}`
  }
  const remainder = value % 1000
  return `${chineseNumber(Math.floor(value / 1000))}千${remainder ? `${remainder < 100 ? '零' : ''}${chineseNumber(remainder)}` : ''}`
}
