// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'

import {
  Banner,
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  Icon,
  Input,
  SegmentedControl,
  Spinner,
  ViewHeader,
} from '@vxture/design-system'

import type { BidMode } from '@/types/tender'

import { BidPageShell } from './components/BidPageShell'
import { ErrorState, LoadingState } from './components/Feedback'
import { useBidWorkspaceQuery, useCreateBidMutation, useSaveSetupMutation } from './queries'

export default function BidSetupPage() {
  const { bidId = '' } = useParams()
  const isNew = !bidId
  const navigate = useNavigate()
  const query = useBidWorkspaceQuery(bidId)
  const createMutation = useCreateBidMutation()
  const updateMutation = useSaveSetupMutation(bidId)
  const [title, setTitle] = useState('')
  const [targetPages, setTargetPages] = useState(100)
  const [mode, setMode] = useState<BidMode>('OPEN')
  const [validation, setValidation] = useState('')

  useEffect(() => {
    if (!query.data) return
    setTitle(query.data.bid.title === '未命名投标文件' ? '' : query.data.bid.title)
    setTargetPages(query.data.bid.targetPages)
    setMode(query.data.bid.biddingMode)
  }, [query.data])

  if (!isNew && query.isPending) return <LoadingState label="正在读取标书设置" />
  if (!isNew && (query.error || !query.data))
    return <ErrorState error={query.error ?? new Error('标书不存在')} />
  const workspace = query.data

  const save = async () => {
    setValidation('')
    if (title.trim().length < 2) {
      setValidation('请输入至少2个字符的标书标题')
      return
    }
    if (targetPages < 20 || targetPages > 2000) {
      setValidation('预设页数必须在20至2000之间')
      return
    }
    try {
      if (isNew) {
        const created = await createMutation.mutateAsync({
          title: title.trim(),
          targetPages,
          biddingMode: mode,
        })
        navigate(`/planner/bids/${created.bid.id}/interpretation`, { replace: true })
      } else if (workspace) {
        await updateMutation.mutateAsync({
          title: title.trim(),
          targetPages,
          biddingMode: mode,
          revision: workspace.bid.revision,
        })
        navigate(`/planner/bids/${bidId}/interpretation`)
      }
    } catch {
      // Mutation error is rendered in the form.
    }
  }

  return (
    <BidPageShell bid={workspace?.bid} current="SETUP">
      <div className="mx-auto w-full max-w-content-narrow-lg px-page-inset py-page-inset">
        <ViewHeader title="标书设置" description="设置本次编制的基本边界" />

        <form
          onSubmit={(event) => {
            event.preventDefault()
            void save()
          }}
        >
          <Card className="border border-border">
            <CardHeader>
              <CardTitle>基础信息</CardTitle>
            </CardHeader>
            <CardContent>
              <FieldGroup>
                <Field>
                  <FieldLabel htmlFor="bid-title">标书标题</FieldLabel>
                  <Input
                    id="bid-title"
              value={title}
              maxLength={160}
              placeholder="例如：某某项目技术标投标文件"
              onChange={(event) => setTitle(event.target.value)}
                  />
                </Field>

                <Field className="max-w-panel-sm">
                  <FieldLabel htmlFor="target-pages">预设标书页数</FieldLabel>
                  <div className="flex items-center gap-xs">
                    <Input
                      id="target-pages"
                      className="w-24 min-w-24"
                      type="number"
                      min={20}
                      max={2000}
                      value={targetPages}
                      onFocus={(event) => event.currentTarget.select()}
                      onChange={(event) => setTargetPages(Number(event.target.value))}
                    />
                    <span className="text-body-sm text-muted-foreground">页</span>
                  </div>
                  <FieldDescription>用于分配章节篇幅，最终页数受排版影响</FieldDescription>
                </Field>

                <Field>
                  <FieldLabel>投标方式</FieldLabel>
                  <SegmentedControl
                    fill
                    ariaLabel="投标方式"
                    value={mode}
                    onChange={setMode}
                    items={[
                      { value: 'OPEN', label: '明标', icon: 'eye' },
                      { value: 'BLIND', label: '暗标', icon: 'eye-slash' },
                    ]}
                  />
                  <FieldDescription>
                    {mode === 'OPEN'
                      ? '允许在正文中出现投标人名称及品牌信息'
                      : '编写时重点核对匿名、字体和版式要求'}
                  </FieldDescription>
                </Field>

                {validation ? <Banner tone="danger" title={validation} /> : null}
                <ErrorState error={createMutation.error ?? updateMutation.error} />
              </FieldGroup>
            </CardContent>

            <CardFooter className="justify-between">
              <Button type="button" variant="outline" onClick={() => navigate('/planner/writing')}>
                <Icon name="arrow-left" size="sm" />
                返回选择
              </Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {createMutation.isPending || updateMutation.isPending ? (
                  <Spinner size="sm" />
                ) : null}
                保存并下一步
                <Icon name="arrow-right" size="sm" />
              </Button>
            </CardFooter>
          </Card>
        </form>
      </div>
    </BidPageShell>
  )
}
