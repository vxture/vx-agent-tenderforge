// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { Banner, Spinner } from '@vxture/design-system'

import { rejectionNotice } from '@/features/entitlement/rejection'
import { SubscriptionNotice } from '@/features/entitlement/SubscriptionNotice'

export function LoadingState({ label = '正在加载' }: { label?: string }) {
  return (
    <div className="flex min-h-row-6xl items-center justify-center gap-xs text-body-sm text-muted-foreground">
      <Spinner size="sm" />
      <span>{label}</span>
    </div>
  )
}

/**
 * 标书工作区里所有命令失败的统一展示。
 *
 * 被 C2 权益拒绝的命令（未订阅 / 失效 / 档位不含 / 额度用尽）渲染订阅引导而不是一句
 * 「请求失败」——后者让人以为是产品坏了，而真正的出路在 console。其余失败照旧。
 */
export function ErrorState({ error }: { error: unknown }) {
  if (!error) return null
  const notice = rejectionNotice(error)
  if (notice) return <SubscriptionNotice notice={notice} />
  return (
    <Banner
      tone="danger"
      title={error instanceof Error ? error.message : '请求失败，请稍后重试'}
    />
  )
}
