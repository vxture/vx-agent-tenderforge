// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { Banner, Spinner } from '@vxture/design-system'

export function LoadingState({ label = '正在加载' }: { label?: string }) {
  return (
    <div className="flex min-h-row-6xl items-center justify-center gap-xs text-body-sm text-muted-foreground">
      <Spinner size="sm" />
      <span>{label}</span>
    </div>
  )
}

export function ErrorState({ error }: { error: unknown }) {
  if (!error) return null
  return (
    <Banner
      tone="danger"
      title={error instanceof Error ? error.message : '请求失败，请稍后重试'}
    />
  )
}
