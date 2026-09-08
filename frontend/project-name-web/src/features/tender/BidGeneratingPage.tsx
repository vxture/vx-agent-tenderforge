// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'

import {
  Banner,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ConfirmDestructive,
  Icon,
  Progress,
  Spinner,
} from '@vxture/design-system'

import type { BidGenerationProgress } from '@/types/tender'

import { BidPageShell } from './components/BidPageShell'
import { ErrorState, LoadingState } from './components/Feedback'
import {
  useBidGenerationProgressQuery,
  useBidMetadataQuery,
  useGenerateContentMutation,
  usePauseContentGenerationMutation,
  useResumeContentGenerationMutation,
} from './queries'

export default function BidGeneratingPage() {
  const { bidId = '' } = useParams()
  const navigate = useNavigate()
  const [pauseDialogOpen, setPauseDialogOpen] = useState(false)
  const metadataQuery = useBidMetadataQuery(bidId)
  const progressQuery = useBidGenerationProgressQuery(bidId)
  const generateMutation = useGenerateContentMutation(bidId)
  const pauseMutation = usePauseContentGenerationMutation(bidId)
  const resumeMutation = useResumeContentGenerationMutation(bidId)
  const metadata = metadataQuery.data
  const task = progressQuery.data
  const reviewIssues = metadata?.production.reviewIssues ?? []
  const blockingIssues = reviewIssues.filter((issue) => issue.status === 'OPEN' && issue.severity === 'ERROR')
  const presentation = task ? generationPresentation(task) : null

  useEffect(() => {
    if (task?.status === 'SUCCEEDED') {
      navigate(`/planner/bids/${bidId}/content`, { replace: true })
    }
  }, [bidId, navigate, task?.status])

  if (metadataQuery.isPending || progressQuery.isPending) {
    return <LoadingState label="正在读取正文编写任务" />
  }
  if (metadataQuery.error || progressQuery.error || !metadata || !task) {
    return (
      <ErrorState
        error={metadataQuery.error ?? progressQuery.error ?? new Error('标书不存在')}
      />
    )
  }

  const running = ['PENDING', 'RUNNING'].includes(task.status)
  const taskError = task.status === 'FAILED' ? new Error(task.errorMessage ?? '正文生成失败') : null
  const mutationError =
    generateMutation.error ?? pauseMutation.error ?? resumeMutation.error

  return (
    <BidPageShell bid={metadata.bid} current="CONTENT" fixedContent>
      <main className="h-full overflow-y-auto px-page-inset">
        <section className="mx-auto flex min-h-full w-full max-w-content-narrow-lg flex-col justify-center py-3xl text-center">
          <div className="mx-auto flex size-media-xs items-center justify-center rounded-md bg-primary-muted text-primary-text">
            {running ? <Spinner size="lg" /> : <Icon name="file-text" size="lg" />}
          </div>
          <h1 className="mt-lg text-title-xl">{presentation?.title ?? '正在编写正文'}</h1>
          <p className="mt-xs text-body-sm text-muted-foreground">
            {task.status === 'FAILED'
              ? '正文生成未完成'
              : presentation?.description}
          </p>
          <Progress
            className="mt-xl"
            value={presentation?.progress ?? task.progress}
            aria-label="正文生成进度"
          />
          <div className="mt-xs text-right text-body-sm tabular-nums text-muted-foreground">
            {presentation?.progress ?? task.progress}%
          </div>

          {taskError || mutationError ? (
            <div className="mt-lg">
              <ErrorState error={taskError ?? mutationError} />
            </div>
          ) : null}

          {task.status === 'PAUSED' ? (
            <Banner
              className="mt-lg text-left"
              tone="info"
              title="任务已停止"
              description={`已完成 ${task.completedUnits} 个写作分段并全部保留，继续后只处理剩余内容。`}
            />
          ) : null}

          {task.status === 'FAILED' && blockingIssues.length ? (
            <Card className="mx-auto mt-lg max-w-content-narrow-lg border border-border text-left">
              <CardHeader>
                <CardTitle>当前阻断项</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col gap-sm">
                {blockingIssues.map((issue) => (
                  <Banner
                    key={issue.id}
                    tone="danger"
                    title={issue.message}
                    description={issue.suggestion}
                  />
                ))}
              </CardContent>
            </Card>
          ) : null}

          <div className="mt-xl flex justify-center gap-sm">
            <Button variant="outline" onClick={() => navigate(`/planner/bids/${bidId}/outline`)}>
              <Icon name="arrow-left" size="sm" />
              返回目录
            </Button>
            {running ? (
              <Button
                variant="destructive"
                confirmExempt="停止正文生成由业务确认对话框二次确认，已完成内容会保留"
                disabled={pauseMutation.isPending}
                onClick={() => setPauseDialogOpen(true)}
              >
                {pauseMutation.isPending ? <Spinner size="sm" /> : <Icon name="stop" size="sm" />}
                停止任务
              </Button>
            ) : null}
            {task.status === 'PAUSED' || task.status === 'FAILED' ? (
              <Button
                disabled={resumeMutation.isPending}
                onClick={() => void resumeMutation.mutateAsync(undefined)}
              >
                {resumeMutation.isPending ? (
                  <Spinner size="sm" />
                ) : (
                  <Icon name="play" size="sm" />
                )}
                继续未完成部分
              </Button>
            ) : null}
            {task.status === 'IDLE' ? (
              <Button
                disabled={generateMutation.isPending}
                onClick={() => void generateMutation.mutateAsync(undefined)}
              >
                {generateMutation.isPending ? <Spinner size="sm" /> : <Icon name="play" size="sm" />}
                开始生成
              </Button>
            ) : null}
          </div>

          {task.recentEvents.length ? (
            <Card className="mx-auto mt-xl w-full max-w-content-narrow-lg border border-border text-left">
              <CardHeader>
                <CardTitle>运行事件</CardTitle>
              </CardHeader>
              <CardContent className="max-h-96 divide-y divide-border overflow-y-auto overscroll-contain">
                {task.recentEvents.map((event) => (
                  <div key={event.id} className="py-sm">
                    <div className="text-body-sm">{event.message}</div>
                    <div className="mt-2xs text-body-sm text-muted-foreground">
                      {new Date(event.occurredAt).toLocaleString('zh-CN', { hour12: false })}
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          ) : null}
        </section>
      </main>
      {pauseDialogOpen ? (
        <ConfirmDestructive
          open
          onOpenChange={(open) => {
            if (!open && !pauseMutation.isPending) setPauseDialogOpen(false)
          }}
          verb="停止"
          target="当前正文生成任务"
          consequence={`已完成的 ${task.completedUnits} 个写作分段会保留，稍后可从未完成部分继续。`}
          cancelLabel="继续运行"
          pendingLabel="正在停止"
          onConfirm={async () => {
            await pauseMutation.mutateAsync(undefined)
            setPauseDialogOpen(false)
          }}
        />
      ) : null}
    </BidPageShell>
  )
}

function generationPresentation(task: BidGenerationProgress) {
  const eventTypes = new Set(task.recentEvents.map((event) => event.type))
  if (task.status === 'SUCCEEDED') {
    return { title: '最终成果已完成', description: '正文已通过审查并完成正式排版', progress: 100 }
  }
  if (task.status === 'PAUSED') {
    return {
      title: '正文生成已停止',
      description: `已完成 ${task.completedUnits} / ${task.totalUnits} 个写作分段`,
      progress: Math.min(84, Math.round(task.progress * 0.84)),
    }
  }
  if (eventTypes.has('LAYOUT_STARTED') || eventTypes.has('CONTENT_FROZEN')) {
    return { title: '正在正式排版', description: '正在生成 DOCX 并执行页数与内容质量检查', progress: 96 }
  }
  if (eventTypes.has('AUTO_REVISION_COMPLETED') || eventTypes.has('AUTO_REVIEW_ISSUES')) {
    return { title: '正在自动修订', description: '系统正按审查意见修改正文，完成后将再次审查', progress: 91 }
  }
  if (eventTypes.has('AUTO_REVIEW_STARTED')) {
    return { title: '正在自动审查', description: '正在检查要求覆盖、参数、术语、承诺和暗标合规性', progress: 87 }
  }
  if (task.status === 'PENDING') {
    return { title: '等待正文编写', description: '任务已提交，正在等待后台 Worker', progress: 1 }
  }
  return {
    title: '正在编写正文',
    description: `已完成 ${task.completedUnits} / ${task.totalUnits} 个写作分段`,
    progress: Math.min(84, Math.round(task.progress * 0.84)),
  }
}
