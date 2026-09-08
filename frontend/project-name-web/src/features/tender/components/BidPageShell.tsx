// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useNavigate } from 'react-router'

import { Banner, Button, Icon } from '@vxture/design-system'

import type { BidDocument, BidStep } from '@/types/tender'

import { BidStepBar } from './BidStepBar'

export function BidPageShell({
  bid,
  current,
  fixedContent = false,
  actions,
  children,
}: {
  bid?: BidDocument
  current: BidStep
  fixedContent?: boolean
  actions?: React.ReactNode
  children: React.ReactNode
}) {
  const navigate = useNavigate()
  return (
    <section className="flex h-dvh min-h-0 w-full min-w-0 flex-col overflow-hidden bg-background">
      <header className="flex h-header-xl min-w-0 shrink-0 items-stretch border-b border-border bg-card px-xs sm:px-lg">
        <Button
          variant="ghost"
          size="icon-sm"
          className="my-auto shrink-0"
          onClick={() => navigate(bid ? '/planner/bids' : '/planner/writing')}
          title={bid ? '返回我的标书' : '返回写作方式'}
          aria-label={bid ? '返回我的标书' : '返回写作方式'}
        >
          <Icon name="arrow-left" size="sm" />
        </Button>
        <div className="my-auto w-sidebar-rail min-w-0 shrink-0 px-xs sm:w-sidebar-collapsed lg:w-sidebar-expanded">
          <div className="truncate text-label-md text-foreground">
            {bid?.title ?? '新建投标文件'}
          </div>
          <div className="truncate text-body-sm text-muted-foreground">
            {bid?.code ?? '完成设置后创建标书任务'}
          </div>
        </div>
        <BidStepBar bidId={bid?.id} current={current} furthest={bid?.workflowStep ?? 'SETUP'} />
        {actions ? <div className="my-auto ml-xs shrink-0">{actions}</div> : null}
      </header>
      {bid?.contentStale && bid.status !== 'GENERATING' ? (
        <Banner
          className="shrink-0 rounded-none border-x-0 border-t-0"
          tone="warning"
          title="上游内容已调整"
          description="现有正文已保留，请重新生成或人工复核。"
        />
      ) : null}
      <div
        className={
          fixedContent
            ? 'min-h-0 min-w-0 flex-1 overflow-hidden'
            : 'min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto overscroll-contain'
        }
      >
        {children}
      </div>
    </section>
  )
}
