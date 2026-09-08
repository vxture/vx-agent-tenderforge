// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-27
import { useRef, useState } from 'react'

import {
  ActionMenu,
  type ActionMenuItem,
  Button,
  DataTable,
  type DataTableColumn,
  EmptyState,
  FilterBar,
  Icon,
  type IconName,
  Input,
  ListPageTemplate,
  Pagination,
  ShellPageContainer,
  Tabs,
  TabsList,
  TabsTrigger,
  useListPagination,
  ViewHeader,
} from '@vxture/design-system'

import { QueryError } from '@/components/QueryState'
import type { AssetCategory, BidReferenceAsset } from '@/types/tender'

import { ErrorState } from './components/Feedback'
import {
  useRemoveTenderAssetMutation,
  useTenderAssetsQuery,
  useUploadTenderAssetMutation,
} from './queries'

const tabs: {
  key: AssetCategory
  label: string
  icon: IconName
  accept: string
}[] = [
  { key: 'TEMPLATE', label: '标书范本', icon: 'archive', accept: '.pdf,.doc,.docx,.txt,.md' },
  { key: 'OUTLINE', label: '标书大纲', icon: 'file-text', accept: '.pdf,.doc,.docx,.txt,.md' },
  { key: 'GALLERY', label: '图库', icon: 'image', accept: '.png,.jpg,.jpeg' },
]

const formatSize = (value: number) =>
  value >= 1024 * 1024
    ? `${(value / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(value / 1024))} KB`

const assetColumns: DataTableColumn<BidReferenceAsset>[] = [
  {
    id: 'displayName',
    header: '素材名称',
    cell: (asset) => <span className="text-label-md">{asset.displayName}</span>,
  },
  {
    id: 'fileType',
    header: '文件类型',
    align: 'center',
    width: 'xs',
    cell: (asset) => (
      <span className="text-muted-foreground">
        {asset.originalFileName.split('.').pop()?.toUpperCase() || '-'}
      </span>
    ),
  },
  {
    id: 'fileSize',
    header: '大小',
    align: 'right',
    width: 'xs',
    cell: (asset) => <span className="text-muted-foreground">{formatSize(asset.fileSize)}</span>,
  },
  {
    id: 'createdAt',
    header: '上传时间',
    width: 'md',
    cell: (asset) => (
      <span className="whitespace-nowrap text-muted-foreground">
        {new Date(asset.createdAt).toLocaleString('zh-CN', { hour12: false })}
      </span>
    ),
  },
]

type AssetListContentProps = {
  assets: BidReferenceAsset[]
  currentTab: (typeof tabs)[number]
  category: AssetCategory
  keyword: string
  loading: boolean
  error: unknown
  mutationError: unknown
  uploading: boolean
  removing: boolean
  onRetry: () => void
  onUpload: () => void
  onRemove: (assetId: string) => Promise<void>
  onCategoryChange: (category: AssetCategory) => void
  onKeywordChange: (keyword: string) => void
}

function AssetListContent({
  assets,
  currentTab,
  category,
  keyword,
  loading,
  error,
  mutationError,
  uploading,
  removing,
  onRetry,
  onUpload,
  onRemove,
  onCategoryChange,
  onKeywordChange,
}: AssetListContentProps) {
  const pagination = useListPagination(assets)

  return (
    <ListPageTemplate
      header={
        <ViewHeader
          icon="archive"
          title="素材管理"
          description="维护个人标书范本、大纲和图片素材"
          action={
            <Button disabled={uploading} onClick={onUpload}>
              <Icon name="upload" size="sm" />
              上传{currentTab.label}
            </Button>
          }
        />
      }
      summary={mutationError ? <ErrorState error={mutationError} /> : undefined}
      filters={
        <FilterBar
          count={`共 ${assets.length} 项`}
          onReset={() => onKeywordChange('')}
          resetLabel="清空搜索"
          search={
            <label className="relative block w-full sm:w-panel-sm">
              <Icon
                name="search"
                size="sm"
                className="pointer-events-none absolute left-sm top-1/2 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                className="pl-xl"
                value={keyword}
                placeholder="搜索素材"
                onChange={(event) => onKeywordChange(event.target.value)}
              />
            </label>
          }
        >
          <Tabs value={category} onValueChange={(value) => onCategoryChange(value as AssetCategory)}>
            <TabsList>
              {tabs.map((tab) => (
                <TabsTrigger key={tab.key} value={tab.key}>
                  <Icon name={tab.icon} size="sm" />
                  {tab.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </FilterBar>
      }
      table={
        error ? (
          <QueryError error={error} retry={onRetry} />
        ) : (
          <DataTable
            columns={assetColumns}
            rows={pagination.pageRows}
            rowKey={(asset) => asset.id}
            loading={loading}
            loadingRows={8}
            indexStart={pagination.indexStart}
            labels={{ rowActions: '操作' }}
            rowActions={(asset) => {
              const items: ActionMenuItem[] = [
                {
                  id: 'delete',
                  label: '删除素材',
                  icon: 'trash',
                  danger: true,
                  confirm: {
                    verb: '删除',
                    target: `素材“${asset.displayName}”`,
                    consequence: '删除后该素材将不能再用于后续标书解读，且无法恢复。',
                    cancelLabel: '取消',
                    pendingLabel: '正在删除',
                    onConfirm: () => onRemove(asset.id),
                  },
                },
              ]
              return (
                <ActionMenu
                  label={`${asset.displayName}的操作`}
                  align="end"
                  disabled={removing}
                  items={items}
                />
              )
            }}
            empty={
              <EmptyState
                icon={currentTab.icon}
                title={`暂无${currentTab.label}`}
                description="上传后可在招标文件解读环节选择使用"
                action={
                  <Button variant="outline" onClick={onUpload}>
                    <Icon name="upload" size="sm" />
                    上传文件
                  </Button>
                }
              />
            }
          />
        )
      }
      footer={
        assets.length > 0 ? (
          <Pagination
            page={pagination.page}
            pageCount={pagination.pageCount}
            total={assets.length}
            countLabel={`共 ${assets.length} 项`}
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
  )
}

export default function TenderAssetsPage() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [category, setCategory] = useState<AssetCategory>('TEMPLATE')
  const [keyword, setKeyword] = useState('')
  const query = useTenderAssetsQuery(category, keyword)
  const uploadMutation = useUploadTenderAssetMutation()
  const removeMutation = useRemoveTenderAssetMutation()
  const currentTab = tabs.find((tab) => tab.key === category) ?? tabs[0]

  const upload = async (file: File) => {
    try {
      await uploadMutation.mutateAsync({ category, file })
    } catch {
      // Error is rendered in the page.
    } finally {
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <section className="h-full overflow-y-auto">
      <ShellPageContainer>
        <Input
          ref={inputRef}
          type="file"
          className="sr-only"
          accept={currentTab.accept}
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) void upload(file)
          }}
        />

        <AssetListContent
          key={category}
          assets={query.data ?? []}
          currentTab={currentTab}
          category={category}
          keyword={keyword}
          loading={query.isPending}
          error={query.error}
          mutationError={uploadMutation.error ?? removeMutation.error}
          uploading={uploadMutation.isPending}
          removing={removeMutation.isPending}
          onRetry={() => void query.refetch()}
          onUpload={() => inputRef.current?.click()}
          onRemove={async (assetId) => {
            await removeMutation.mutateAsync(assetId)
          }}
          onCategoryChange={setCategory}
          onKeywordChange={setKeyword}
        />
      </ShellPageContainer>
    </section>
  )
}
