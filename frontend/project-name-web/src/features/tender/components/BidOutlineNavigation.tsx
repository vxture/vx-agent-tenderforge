// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useMemo, useState } from 'react'

import { Button, cn, Icon } from '@vxture/design-system'

import type { BidChapterSummary, BidOutlineNode } from '@/types/tender'

import { buildOutlineLabels } from '../outlineNumbering'

type OutlineTreeItem = {
  node: BidOutlineNode
  children: OutlineTreeItem[]
}

export function BidOutlineNavigation({
  outline,
  chapters,
  activeChapterId,
  onSelect,
}: {
  outline: BidOutlineNode[]
  chapters: BidChapterSummary[]
  activeChapterId: string
  onSelect: (chapterId: string) => void
}) {
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(() => new Set())
  const chapterByNode = useMemo(
    () => new Map(chapters.map((chapter) => [chapter.outlineNodeId, chapter])),
    [chapters]
  )
  const tree = useMemo(() => buildOutlineTree(outline), [outline])
  const labels = useMemo(() => buildOutlineLabels(outline), [outline])
  const parentIds = useMemo(
    () => new Set(outline.map((node) => node.parentId).filter((id): id is string => Boolean(id))),
    [outline]
  )
  const allExpanded = collapsedIds.size === 0

  const toggle = (nodeId: string) => {
    setCollapsedIds((current) => {
      const next = new Set(current)
      if (next.has(nodeId)) next.delete(nodeId)
      else next.add(nodeId)
      return next
    })
  }

  const toggleAll = () => {
    setCollapsedIds(allExpanded ? new Set(parentIds) : new Set())
  }

  return (
    <aside className="hidden w-96 shrink-0 flex-col border-r border-border bg-card md:flex lg:w-[432px] xl:w-[480px]">
      <header className="flex h-header-sm shrink-0 items-center justify-between border-b border-border px-sm">
        <div className="min-w-0">
          <span className="text-label-md">目录</span>
          <span className="ml-xs text-body-sm text-muted-foreground">{chapters.length} 个正文章节</span>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          title={allExpanded ? '全部折叠' : '全部展开'}
          aria-label={allExpanded ? '全部折叠' : '全部展开'}
          onClick={toggleAll}
        >
          {allExpanded ? (
            <Icon name="caret-double-up" size="sm" />
          ) : (
            <Icon name="caret-double-down" size="sm" />
          )}
        </Button>
      </header>
      <nav
        className="min-h-0 flex-1 overflow-y-auto py-xs"
        aria-label="正文三级目录"
        role="tree"
      >
        <OutlineTreeRows
          items={tree}
          chapterByNode={chapterByNode}
          collapsedIds={collapsedIds}
          activeChapterId={activeChapterId}
          labels={labels}
          onSelect={onSelect}
          onToggle={toggle}
        />
      </nav>
    </aside>
  )
}

function OutlineTreeRows({
  items,
  chapterByNode,
  collapsedIds,
  activeChapterId,
  labels,
  onSelect,
  onToggle,
}: {
  items: OutlineTreeItem[]
  chapterByNode: Map<string, BidChapterSummary>
  collapsedIds: Set<string>
  activeChapterId: string
  labels: Map<string, string>
  onSelect: (chapterId: string) => void
  onToggle: (nodeId: string) => void
}) {
  return items.map((item) => {
    const { node, children } = item
    const chapter = chapterByNode.get(node.id)
    const hasChildren = children.length > 0
    const expanded = !collapsedIds.has(node.id)
    const active = chapter?.id === activeChapterId
    return (
      <div key={node.id} role="none">
        <Button
          variant="ghost"
          size="sm"
          role="treeitem"
          aria-level={node.level}
          aria-expanded={hasChildren ? expanded : undefined}
          aria-selected={active}
          disabled={!hasChildren && !chapter}
          onClick={() => {
            if (hasChildren) onToggle(node.id)
            else if (chapter) onSelect(chapter.id)
          }}
          className={cn(
            'min-h-control-xl h-auto w-full justify-start gap-xs rounded-none border-l-2 py-xs pr-sm text-left',
            node.level === 1 && 'text-label-lg',
            node.level === 2 && 'text-label-md',
            node.level === 3 && 'text-body-sm text-muted-foreground',
            active ? 'border-primary bg-surface-selected text-primary-text' : 'border-transparent'
          )}
          style={{ paddingLeft: `${12 + (node.level - 1) * 20}px` }}
        >
          {hasChildren ? (
            expanded ? (
              <Icon name="chevron-down" size="sm" className="mt-2xs shrink-0" />
            ) : (
              <Icon name="chevron-right" size="sm" className="mt-2xs shrink-0" />
            )
          ) : (
            <Icon name="file-text" size="sm" className="mt-2xs shrink-0" />
          )}
          <span className="min-w-0 flex-1 whitespace-normal break-words">
            {labels.get(node.id) ?? node.title}
          </span>
          {chapter && ['READY', 'MANUAL'].includes(chapter.generationStatus) ? (
            <Icon name="check" size="sm" className="mt-2xs shrink-0 text-success-text" />
          ) : null}
        </Button>
        {hasChildren && expanded ? (
          <div role="group">
            <OutlineTreeRows
              items={children}
              chapterByNode={chapterByNode}
              collapsedIds={collapsedIds}
              activeChapterId={activeChapterId}
              labels={labels}
              onSelect={onSelect}
              onToggle={onToggle}
            />
          </div>
        ) : null}
      </div>
    )
  })
}

function buildOutlineTree(outline: BidOutlineNode[]): OutlineTreeItem[] {
  const items = new Map(
    outline.map((node) => [node.id, { node, children: [] as OutlineTreeItem[] }])
  )
  const roots: OutlineTreeItem[] = []
  outline
    .slice()
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .forEach((node) => {
      const item = items.get(node.id)
      if (!item) return
      const parent = node.parentId ? items.get(node.parentId) : undefined
      if (parent) parent.children.push(item)
      else roots.push(item)
    })
  return roots
}
