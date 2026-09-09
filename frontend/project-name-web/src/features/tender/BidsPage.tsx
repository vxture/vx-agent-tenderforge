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
  Pagination,
  ShellPageContainer,
  StatusBadge,
  type StatusBadgeTone,
  useListPagination,
  ViewHeader,
} from '@vxture/design-system'

import { ApiError } from '@/api/client'
import { tenderApi } from '@/api/modules/tender'
import { QueryError } from '@/components/QueryState'
import type { BidExport, BidStatus, BidStep, BidSummary } from '@/types/tender'

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
  const query = useBidsQuery()
  const [downloading, setDownloading] = useState('')
  const [downloadError, setDownloadError] = useState<unknown>(null)
  const bids = query.data ?? []
  const pagination = useListPagination(bids)

  const openBid = (bid: BidSummary) => {
    const destination = ['GENERATING', 'GENERATION_PAUSED'].includes(bid.status)
      ? 'generating'
      : stepRoutes[bid.workflowStep]
    navigate(`/planner/bids/${bid.id}/${destination}`)
  }

  /**
   * 下载最近一次成果。
   *
   * 先列后取是有意的：服务端不再提供 /exports/latest/download——「最新」是一个视角，
   * 把它固化成路径段就再也无法参数化（通则 A-2）。挑哪一个由这里决定，
   * 于是「按版本号最大」这条规则留在了它属于的地方，而不是被焊进一条路由。
   */
  const download = async (bid: BidSummary) => {
    setDownloading(bid.id)
    setDownloadError(null)
    try {
      const exports = await tenderApi.listExports(bid.id)
      const latest = exports.reduce<BidExport | null>(
        (best, item) => (best === null || item.version > best.version ? item : best),
        null
      )
      if (latest === null) {
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
                rows={pagination.pageRows}
                rowKey={(bid) => bid.id}
                loading={query.isPending}
                loadingRows={8}
                indexStart={pagination.indexStart}
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
            bids.length > 0 ? (
              <Pagination
                page={pagination.page}
                pageCount={pagination.pageCount}
                total={bids.length}
                countLabel={`共 ${bids.length} 份`}
                pageSize={pagination.pageSize}
                pageSizeOptions={['auto', 10, 20, 50]}
                onPageChange={pagination.onPageChange}
                onPageSizeChange={pagination.onPageSizeChange}
                previousLabel="上一页"
                nextLabel="下一页"
                pageSizeLabel="每页条数"
                pageSizeOptionTemplate="每页 {size} 条"
                pageSizeAutoLabel="自适应"
              />
            ) : undefined
          }
        />
      </ShellPageContainer>
    </section>
  )
}
