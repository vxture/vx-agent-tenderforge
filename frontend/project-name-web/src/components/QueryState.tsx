// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { Banner, Button, EmptyState, Icon, Spinner } from '@vxture/design-system'

import { ApiError } from '@/api/client'

export function QueryLoading({ label = '正在加载' }: { label?: string }) {
  return (
    <div className="flex min-h-row-6xl items-center justify-center gap-xs text-body-sm text-muted-foreground">
      <Spinner size="sm" />
      <span>{label}</span>
    </div>
  )
}

export function QueryError({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <EmptyState
      className="min-h-row-6xl"
      icon="error"
      title="加载失败"
      description={errorMessage(error)}
      action={
        retry ? (
        <Button variant="outline" size="sm" onClick={retry}>
          <Icon name="refresh" size="sm" />
          重试
        </Button>
        ) : undefined
      }
    />
  )
}

export function MutationError({ error }: { error: unknown }) {
  if (!error) return null
  return <Banner className="mt-sm" tone="danger" title={errorMessage(error)} />
}

function errorMessage(error: unknown) {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '请求失败，请稍后重试'
}
