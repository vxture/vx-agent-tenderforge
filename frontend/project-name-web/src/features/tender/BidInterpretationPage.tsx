// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'

import {
  Banner,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Checkbox,
  EmptyState,
  Icon,
  Input,
  Progress,
  Spinner,
  StatusBadge,
  Textarea,
  ViewHeader,
} from '@vxture/design-system'

import type { BidCriterion, CriterionInput, InterpretationObjectType } from '@/types/tender'
import { createClientId } from '@/utils/clientId'

import { BidPageShell } from './components/BidPageShell'
import { ErrorState, LoadingState } from './components/Feedback'
import { InterpretationFreezePanel } from './components/InterpretationFreezePanel'
import { InterpretationObjectStatus } from './components/InterpretationObjectStatus'
import {
  useBidWorkspaceQuery,
  useFreezeInterpretationMutation,
  useParseSourceMutation,
  useSaveCriteriaMutation,
  useSelectAssetsMutation,
  useTenderAssetsQuery,
  useUploadSourceMutation,
} from './queries'

const interpretationObjects: {
  type: InterpretationObjectType
  title: string
  description: string
  placeholder: string
}[] = [
  {
    type: 'PROJECT_OVERVIEW',
    title: '项目概述',
    description: '项目背景、建设目标、建设内容、技术范围、交付与实施服务等项目内容',
    placeholder: 'DeepSeek 解析后将在这里生成项目概述，也可直接人工填写或追加内容。',
  },
  {
    type: 'TECHNICAL_SCORING',
    title: '技术部分评分要求',
    description: '技术评分大项、子项、分值、得分档位、评分条件和证明材料要求',
    placeholder: 'DeepSeek 解析后将在这里生成技术部分评分要求，也可直接人工填写或追加内容。',
  },
]

const parseStatusLabels = {
  PENDING: '待解析',
  PARSING: '解析中',
  SUCCEEDED: '解析完成',
  FAILED: '解析失败',
} as const

const parseStageLabels = {
  PENDING: '等待开始解析',
  QUEUED: '任务已提交，正在排队',
  EXTRACTING: '正在读取招标文件文字和表格',
  EXTRACTED: '文字和表格已提取，准备生成解读结果',
  PROJECT_OVERVIEW: 'DeepSeek 正在提取项目概述',
  TECHNICAL_SCORING: 'DeepSeek 正在提取技术部分评分要求',
  SAVING: '正在保存解读结果',
  COMPLETE: '解析完成',
  FAILED: '解析失败',
} as const

export default function BidInterpretationPage() {
  const { bidId = '' } = useParams()
  const navigate = useNavigate()
  const fileInput = useRef<HTMLInputElement>(null)
  const dirtyObjects = useRef(new Set<InterpretationObjectType>())
  const query = useBidWorkspaceQuery(bidId)
  const uploadMutation = useUploadSourceMutation(bidId)
  const parseMutation = useParseSourceMutation(bidId)
  const saveMutation = useSaveCriteriaMutation(bidId)
  const selectionMutation = useSelectAssetsMutation(bidId)
  const freezeMutation = useFreezeInterpretationMutation(bidId)
  const assetsQuery = useTenderAssetsQuery()
  const [criteria, setCriteria] = useState<CriterionInput[]>([])
  const [selectedAssetIds, setSelectedAssetIds] = useState<string[]>([])
  const [validation, setValidation] = useState('')

  useEffect(() => {
    if (!query.data) return
    const parsedCriteria =
      query.data.sourceFile?.parseStatus === 'PENDING' ? [] : query.data.criteria
    setCriteria((current) =>
      interpretationObjects.map((object) => {
        if (dirtyObjects.current.has(object.type)) {
          const edited = current.find((item) => item.type === object.type)
          if (edited) return edited
        }
        return toInput(
          parsedCriteria.find((item) => item.type === object.type),
          object.type,
          object.title
        )
      })
    )
    setSelectedAssetIds(query.data.selectedAssetIds)
  }, [query.data])

  if (query.isPending) return <LoadingState label="正在读取招标文件解读" />
  if (query.error || !query.data) {
    return <ErrorState error={query.error ?? new Error('标书不存在')} />
  }
  const workspace = query.data
  const isParsing = workspace.sourceFile?.parseStatus === 'PARSING'
  const hasCompletedParsing = workspace.sourceFile?.parseStatus === 'SUCCEEDED'
  const hasPartialSuccess =
    workspace.sourceFile?.parseStatus === 'FAILED' &&
    [workspace.sourceFile.overviewStatus, workspace.sourceFile.scoringStatus].includes('SUCCEEDED')
  const referenceAssets = assetsQuery.data?.filter((asset) => asset.category !== 'GALLERY') ?? []
  const persisting =
    saveMutation.isPending || selectionMutation.isPending || freezeMutation.isPending

  const upload = async (file: File) => {
    try {
      await uploadMutation.mutateAsync(file)
      dirtyObjects.current.clear()
      setCriteria(interpretationObjects.map((item) => emptyCriterion(item.type, item.title)))
    } catch {
      // Error is rendered below the upload control.
    } finally {
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  const parse = async () => {
    if (workspace.sourceFile?.parseStatus === 'SUCCEEDED') {
      dirtyObjects.current.clear()
    }
    try {
      await parseMutation.mutateAsync(undefined)
    } catch {
      // Error is rendered below the source control.
    }
  }

  const validate = () => {
    const missing = interpretationObjects.find(
      (object) => !criteria.find((item) => item.type === object.type)?.description.trim()
    )
    if (missing) {
      setValidation(`请填写${missing.title}`)
      return false
    }
    setValidation('')
    return true
  }

  const persistAndFreeze = async () => {
    if (!validate()) return null
    await saveMutation.mutateAsync({
      items: criteria,
      revision: workspace.bid.revision,
    })
    dirtyObjects.current.clear()
    const selected = await selectionMutation.mutateAsync(selectedAssetIds)
    if (selected.production.interpretationStatus === 'FROZEN') return selected
    return freezeMutation.mutateAsync(selected.bid.revision)
  }

  const saveAndContinue = async () => {
    try {
      const result = await persistAndFreeze()
      if (result) navigate(`/planner/bids/${bidId}/outline`)
    } catch {
      // Mutation errors are rendered below the editor.
    }
  }

  return (
    <BidPageShell bid={workspace.bid} current="INTERPRETATION">
      <div className="mx-auto w-full max-w-content-wide-2xl px-page-inset py-page-inset">
        <ViewHeader
          icon="file-text"
          title="招标文件解读"
          description="提取并核对项目概述和技术部分评分要求"
        />

        <div className="flex flex-col gap-lg">
          <Card className="border border-border">
            <CardHeader>
              <CardTitle>招标文件</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap items-center gap-sm">
                <div className="text-label-md">
                  文件 <span className="text-destructive">*</span>
                </div>
                <div className="flex h-control-md min-w-0 flex-1 items-center gap-xs rounded-md border border-input px-sm">
                  <Icon
                    name={workspace.sourceFile ? 'success' : 'file'}
                    size="sm"
                    className={workspace.sourceFile ? 'text-success-text' : 'text-muted-foreground'}
                  />
                  <span className="min-w-0 flex-1 truncate text-body-sm">
                    {workspace.sourceFile?.originalFileName ?? '尚未上传招标文件'}
                  </span>
                  {workspace.sourceFile ? (
                    <StatusBadge
                      tone={
                        workspace.sourceFile.parseStatus === 'SUCCEEDED'
                          ? 'success'
                          : workspace.sourceFile.parseStatus === 'FAILED'
                            ? 'danger'
                            : workspace.sourceFile.parseStatus === 'PARSING'
                              ? 'info'
                              : 'neutral'
                      }
                    >
                      {parseStatusLabels[workspace.sourceFile.parseStatus]}
                    </StatusBadge>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-xs">
                  <Button
                    variant="outline"
                    disabled={uploadMutation.isPending || isParsing}
                    onClick={() => fileInput.current?.click()}
                  >
                    {uploadMutation.isPending ? (
                      <Spinner size="sm" />
                    ) : (
                      <Icon name="upload" size="sm" />
                    )}
                    {workspace.sourceFile ? '重新上传' : '上传文件'}
                  </Button>
                  <Button
                    disabled={!workspace.sourceFile || parseMutation.isPending || isParsing}
                    onClick={() => void parse()}
                  >
                    {parseMutation.isPending ? (
                      <Spinner size="sm" />
                    ) : (
                      <Icon name="sparkles" size="sm" />
                    )}
                    {parseMutation.isPending
                      ? '正在提交'
                      : isParsing
                        ? '解析进行中'
                        : hasPartialSuccess
                          ? '仅重试失败对象'
                          : hasCompletedParsing
                            ? '重新解析'
                            : '开始解析'}
                  </Button>
                </div>
              </div>
              <Input
                ref={fileInput}
                type="file"
                className="sr-only"
                accept=".pdf,.doc,.docx,.xlsx,.xlsm,.csv,.txt,.md"
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (file) void upload(file)
                }}
              />
              <p className="mt-xs text-body-sm text-muted-foreground">
                支持 PDF、Word、Excel、CSV、TXT，单文件不超过50MB
              </p>
              {isParsing && workspace.sourceFile ? (
                <Banner
                  className="mt-lg"
                  tone="info"
                  title={
                    <span className="flex items-center gap-xs">
                      <Spinner size="sm" />
                      {parseStageLabels[workspace.sourceFile.parseStage]}
                    </span>
                  }
                  description={
                    <span className="flex flex-col gap-xs">
                      <Progress
                        value={workspace.sourceFile.parseProgress}
                        aria-label="招标文件解析进度"
                      />
                      <span>
                        {workspace.sourceFile.parseProgress}% · 任务在后台持续执行，可以刷新页面或稍后返回。
                      </span>
                    </span>
                  }
                />
              ) : null}
              {workspace.sourceFile?.parseStatus === 'FAILED' ? (
                <Banner
                  className="mt-sm"
                  tone="danger"
                  title={workspace.sourceFile.errorMessage || '解析失败，请重试'}
                />
              ) : null}
              <div className="mt-sm">
                <ErrorState error={uploadMutation.error ?? parseMutation.error} />
              </div>
            </CardContent>
          </Card>

          <Card className="border border-border">
            <CardHeader>
              <CardTitle>解读结果</CardTitle>
              <p className="text-body-sm text-muted-foreground">
                DeepSeek 仅提取以下两个业务对象，内容支持人工修改和追加
              </p>
            </CardHeader>
            <CardContent className="divide-y divide-border">
              {interpretationObjects.map((object) => {
                const item =
                  criteria.find((criterion) => criterion.type === object.type) ??
                  emptyCriterion(object.type, object.title)
                const objectStatus =
                  object.type === 'PROJECT_OVERVIEW'
                    ? (workspace.sourceFile?.overviewStatus ?? 'PENDING')
                    : (workspace.sourceFile?.scoringStatus ?? 'PENDING')
                const objectError =
                  object.type === 'PROJECT_OVERVIEW'
                    ? workspace.sourceFile?.overviewErrorMessage
                    : workspace.sourceFile?.scoringErrorMessage
                const objectPending =
                  objectStatus === 'RUNNING' || (objectStatus === 'PENDING' && isParsing)
                return (
                  <label key={object.type} className="block py-xl">
                    <span className="flex flex-wrap items-baseline justify-between gap-xs">
                      <span>
                        <span className="block text-label-md">{object.title}</span>
                        <span className="mt-2xs block text-body-sm text-muted-foreground">
                          {object.description}
                        </span>
                      </span>
                      <span className="flex items-center gap-xs">
                        <InterpretationObjectStatus status={objectStatus} />
                        <span className="text-body-sm tabular-nums text-muted-foreground">
                          {item.description.length.toLocaleString('zh-CN')} 字符
                        </span>
                      </span>
                    </span>
                    <Textarea
                      className="mt-sm min-h-panel-md resize-y whitespace-pre-wrap font-mono"
                      value={item.description}
                      disabled={objectPending}
                      placeholder={object.placeholder}
                      onChange={(event) => {
                        dirtyObjects.current.add(object.type)
                        setCriteria((current) =>
                          current.map((criterion) =>
                            criterion.type === object.type
                              ? { ...criterion, description: event.target.value }
                              : criterion
                          )
                        )
                      }}
                    />
                    {objectError ? (
                      <Banner className="mt-xs" tone="danger" title={objectError} />
                    ) : null}
                  </label>
                )
              })}
            </CardContent>
          </Card>

          <InterpretationFreezePanel
            production={workspace.production}
            freezing={persisting}
            onFreeze={async () => {
              try {
                await persistAndFreeze()
              } catch {
                // Mutation errors are rendered below the editor.
              }
            }}
          />

          <Card className="border border-border">
            <CardHeader className="flex-row flex-wrap items-center justify-between gap-sm">
              <div>
                <CardTitle>参考素材</CardTitle>
                <p className="mt-2xs text-body-sm text-muted-foreground">
                  标书大纲用于目录编写，标书范本用于正文编写
                </p>
              </div>
              <span className="text-body-sm text-muted-foreground">
                已选择 {selectedAssetIds.length} 项
              </span>
            </CardHeader>
            <CardContent>
              {assetsQuery.isPending ? <LoadingState label="加载素材" /> : null}
              {!assetsQuery.isPending && referenceAssets.length === 0 ? (
                <EmptyState icon="archive" title="暂无标书大纲或标书范本" />
              ) : null}
              {referenceAssets.length ? (
                <div className="grid gap-xs sm:grid-cols-2">
                  {referenceAssets.map((asset) => {
                    const selected = selectedAssetIds.includes(asset.id)
                    return (
                      <label
                        key={asset.id}
                        className={`flex min-w-0 cursor-pointer items-center gap-sm rounded-md border px-md py-sm transition-colors ${
                          selected
                            ? 'border-primary bg-surface-selected'
                            : 'border-border hover:bg-accent'
                        }`}
                      >
                        <Checkbox
                          checked={selected}
                          onCheckedChange={(checked) =>
                            setSelectedAssetIds((current) =>
                              checked === true
                                ? [...current, asset.id]
                                : current.filter((id) => id !== asset.id)
                            )
                          }
                        />
                        <span className="min-w-0 flex-1">
                          <span className="block truncate text-label-md">
                            {asset.displayName}
                          </span>
                          <span className="mt-2xs block text-body-sm text-muted-foreground">
                            {asset.category === 'TEMPLATE' ? '标书范本' : '标书大纲'}
                          </span>
                        </span>
                      </label>
                    )
                  })}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        {validation ? <Banner className="mt-lg" tone="danger" title={validation} /> : null}
        <div className="mt-lg">
          <ErrorState
            error={saveMutation.error ?? selectionMutation.error ?? freezeMutation.error}
          />
        </div>
        <div className="mt-xl flex flex-wrap justify-between gap-sm border-t border-border pt-lg">
          <Button variant="outline" onClick={() => navigate(`/planner/bids/${bidId}/setup`)}>
            <Icon name="arrow-left" size="sm" />
            上一步
          </Button>
          <Button
            disabled={isParsing || !hasCompletedParsing || persisting}
            onClick={() => void saveAndContinue()}
          >
            {persisting ? <Spinner size="sm" /> : null}
            保存并下一步
            <Icon name="arrow-right" size="sm" />
          </Button>
        </div>
      </div>
    </BidPageShell>
  )
}

const toInput = (
  item: BidCriterion | undefined,
  type: InterpretationObjectType,
  title: string
): CriterionInput =>
  item
    ? {
        id: item.id,
        type,
        title,
        description: item.description,
        score: null,
        sourceExcerpt: '',
        sourceLocator: '',
        scope: 'TECHNICAL',
        confidence: 'HIGH',
      }
    : emptyCriterion(type, title)

const emptyCriterion = (type: InterpretationObjectType, title: string): CriterionInput => ({
  id: createClientId(),
  type,
  title,
  description: '',
  score: null,
  sourceExcerpt: '',
  sourceLocator: '',
  scope: 'TECHNICAL',
  confidence: 'HIGH',
})
