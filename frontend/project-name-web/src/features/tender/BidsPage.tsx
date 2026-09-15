// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
import { useState } from 'react'
import { useNavigate } from 'react-router'

import {
  ActionMenu,
  type ActionMenuItem,
  Button,
  DataTable,
  type DataTableColumn,
  EmptyState,
  ListPageTemplate,
  ShellPageContainer,
  StatusBadge,
  type StatusBadgeTone,
  ViewHeader,
} from '@vxture/design-system'

import { ApiError } from '@/api/client'
import { tenderApi } from '@/api/modules/tender'
import { QueryError } from '@/components/QueryState'
import type { BidStatus, BidStep, BidSummary } from '@/types/tender'
import { useCursorPager } from '@/utils/cursorPager'

import { CursorPagerFooter } from './components/CursorPagerFooter'
import { ErrorState } from './components/Feedback'
import { useBidsQuery } from './queries'

const statusLabels: Record<BidStatus, string> = {
  DRAFT: '待设置',
  PARSING: '解析中',
  INTERPRETATION_READY: '解读完成',
  OUTLINE_GENERATING: '目录生成中',
  OUTLINE_READY: '目录已确认',
  GENERATING: '正文生成中',
  GENERATION_PAUSED: '正文已暂停',
  CONTENT_READY: '正文可编辑',
  LAYOUT_QUEUED: '等待排版',
  LAYOUT_RUNNING: '正式排版中',
  COMPLETED: '已完成',
  EXPORTED: '已有成果',
  FAILED: '处理失败',
}

const statusTones: Record<BidStatus, StatusBadgeTone> = {
  DRAFT: 'neutral',
  PARSING: 'info',
  INTERPRETATION_READY: 'success',
  OUTLINE_GENERATING: 'info',
  OUTLINE_READY: 'success',
  GENERATING: 'brand',
  GENERATION_PAUSED: 'warning',
  CONTENT_READY: 'success',
  LAYOUT_QUEUED: 'neutral',
  LAYOUT_RUNNING: 'info',
  COMPLETED: 'success',
  EXPORTED: 'success',
  FAILED: 'danger',
}

const stepRoutes: Record<BidStep, string> = {
  SETUP: 'setup',
  INTERPRETATION: 'interpretation',
  OUTLINE: 'outline',
  CONTENT: 'content',
}

const formatDate = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false })

function BidStatusCell({ bid }: { bid: BidSummary }) {
  return (
    <div>
      <StatusBadge tone={statusTones[bid.status]}>{statusLabels[bid.status]}</StatusBadge>
      {bid.contentStale ? <div className="mt-2xs text-body-sm text-warning-text">待复核</div> : null}
    </div>
  )
}

const bidColumns = (openBid: (bid: BidSummary) => void): DataTableColumn<BidSummary>[] => [
  {
    id: 'title',
    header: '标书名称',
    cell: (bid) => (
      <div className="min-w-0">
        <Button
          variant="link"
          className="max-w-sidebar-expanded justify-start truncate px-none"
          onClick={() => openBid(bid)}
        >
          {bid.title}
        </Button>
        <div className="mt-2xs text-body-sm text-muted-foreground">{bid.code}</div>
      </div>
    ),
  },
  {
    id: 'biddingMode',
    header: '投标方式',
    align: 'center',
    width: 'xs',
    cell: (bid) => (bid.biddingMode === 'BLIND' ? '暗标' : '明标'),
  },
  {
    id: 'status',
    header: '当前状态',
    align: 'center',
    width: 'md',
    cell: (bid) => <BidStatusCell bid={bid} />,
  },
  {
    id: 'progress',
    header: '正文进度',
    align: 'center',
    width: 'sm',
    cell: (bid) =>
      bid.totalChapters > 0 ? `${bid.completedChapters} / ${bid.totalChapters}` : '-',
  },
  {
    id: 'updatedAt',
    header: '更新时间',
    width: 'md',
    cell: (bid) => <span className="whitespace-nowrap text-muted-foreground">{formatDate(bid.updatedAt)}</span>,
  },
]

export default function BidsPage() {
  const navigate = useNavigate()
  const pager = useCursorPager()
  const query = useBidsQuery(pager.cursor)
  const [downloading, setDownloading] = useState('')
  const [downloadError, setDownloadError] = useState<unknown>(null)
  const bids = query.data?.items ?? []

  const openBid = (bid: BidSummary) => {
    const destination = ['GENERATING', 'GENERATION_PAUSED'].includes(bid.status)
      ? 'generating'
      : stepRoutes[bid.workflowStep]
    navigate(`/planner/bids/${bid.id}/${destination}`)
  }

  /**
   * 下载最近一次成果。
   *
   * 先列后取是有意的：服务端不提供 /exports/latest/download——「最新」是一个视角，
   * 把它固化成路径段就再也无法参数化（通则 A-2）。导出列表按生成时间倒序、游标分页，
   * 所以只取一条：limit=1 的第一条就是最近生成的那份，不必把全部版本拉回来再挑。
   */
  const download = async (bid: BidSummary) => {
    setDownloading(bid.id)
    setDownloadError(null)
    try {
      const latest = (await tenderApi.listExports(bid.id, { limit: 1 })).items[0]
      if (!latest) {
        throw new ApiError('该标书尚无可下载成果', 404, 'BID_EXPORT_NOT_FOUND', false)
      }
      await tenderApi.downloadExport(bid.id, latest.id, bid.title)
    } catch (error) {
      setDownloadError(error)
    } finally {
      setDownloading('')
    }
  }

  const bidActions = (bid: BidSummary) => {
    const items: ActionMenuItem[] = [
      {
        id: 'continue',
        label: '继续编写',
        icon: 'edit',
        onSelect: () => openBid(bid),
      },
    ]
    if (bid.hasExport) {
      items.push({
        id: 'download',
        label: bid.latestExportVersion ? `下载最新成果 v${bid.latestExportVersion}` : '下载最新成果',
        icon: 'download',
        disabled: downloading === bid.id,
        hint: downloading === bid.id ? '正在下载最新成果' : undefined,
        onSelect: () => void download(bid),
      })
    }
    return <ActionMenu label={`${bid.title}的操作`} align="end" items={items} />
  }

  return (
    <section className="h-full overflow-y-auto">
      <ShellPageContainer>
        <ListPageTemplate
          header={
            <ViewHeader
              icon="file-text"
              title="我的标书"
              description="查看进度、继续编写并下载最新成果"
            />
          }
          summary={downloadError ? <ErrorState error={downloadError} /> : undefined}
          table={
            query.isError ? (
              <QueryError error={query.error} retry={() => void query.refetch()} />
            ) : (
              <DataTable
                columns={bidColumns(openBid)}
                rows={bids}
                rowKey={(bid) => bid.id}
                loading={query.isPending}
                loadingRows={8}
                rowActions={bidActions}
                labels={{ rowActions: '操作' }}
                empty={
                  <EmptyState
                    icon="file"
                    title="还没有标书"
                    description="从评分点写作开始创建第一份投标文件"
                    action={<Button onClick={() => navigate('/planner/writing')}>开始编写</Button>}
                  />
                }
              />
            )
          }
          footer={
            query.data && (bids.length > 0 || pager.canGoPrevious) ? (
              <CursorPagerFooter
                count={bids.length}
                unit="份"
                canGoPrevious={pager.canGoPrevious}
                nextCursor={query.data.nextCursor}
                onPrevious={pager.previous}
                onNext={pager.next}
              />
            ) : undefined
          }
        />
      </ShellPageContainer>
    </section>
  )
}
