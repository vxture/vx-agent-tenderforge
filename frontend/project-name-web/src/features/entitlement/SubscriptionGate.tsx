// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, ResultPageTemplate, ShellBootScreen } from '@vxture/design-system'

import { entitlementApi } from '@/api/modules/entitlement'

import type { AccessState } from './access'
import { accessState, formatDate } from './access'
import { entitlementKeys, useEntitlementQuery } from './queries'

type BlockedAccess = Exclude<AccessState, { kind: 'granted' }>

/**
 * 订阅闸门：已登录不等于能用。进入产品界面之前按 C2 权益判定，没有就停在这一屏。
 *
 * - 只挡界面，不替代服务端判定：命令入口的 `EntitlementGuard` 仍是强制点。这里挡的是
 *   「进得去、处处 403」——2026-09-15 生产上未订阅用户登录后直接进了智能体。
 * - 深链只在**显式点击**时新开窗口打开，永不自动跳转（通则 C2）。
 * - 「我已完成订阅」调 `POST /api/entitlement/refresh`：服务端先驱逐自己工作空间的缓存再问平台，
 *   不让刚付完钱的人再看 45 秒旧答案。
 *
 * `standalone`：全屏标书页没有页头，停在这一屏时要给一条回工作台的路。
 */
export function SubscriptionGate({
  children,
  standalone = false,
}: {
  children: ReactNode
  standalone?: boolean
}) {
  const entitlement = useEntitlementQuery()
  const queryClient = useQueryClient()
  const recheck = useMutation({
    mutationFn: entitlementApi.refresh,
    onSuccess: (view) => queryClient.setQueryData(entitlementKeys.current, view),
    onError: () => entitlement.refetch(),
  })

  if (entitlement.isPending) {
    return <ShellBootScreen label="TenderAgent" description="正在确认订阅状态" delayMs={250} />
  }
  const access = entitlement.isError ? ({ kind: 'unavailable' } as const) : accessState(entitlement.data)
  if (access.kind === 'granted') return <>{children}</>

  const copy = copyFor(access)
  const subscribeUrl = entitlement.data?.subscribeUrl
  return (
    <div className="flex min-h-full items-center justify-center p-md">
      <ResultPageTemplate
        tone={copy.tone}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-wrap items-center justify-center gap-xs">
            {copy.subscribeLabel && subscribeUrl ? (
              <Button onClick={() => window.open(subscribeUrl, '_blank', 'noopener,noreferrer')}>
                {copy.subscribeLabel}
              </Button>
            ) : null}
            <Button
              variant="outline"
              disabled={recheck.isPending || entitlement.isFetching}
              onClick={() => recheck.mutate()}
            >
              {access.kind === 'unavailable' ? '重试' : '我已完成订阅'}
            </Button>
            {standalone ? (
              <Button variant="outline" asChild>
                <Link to="/">返回工作台</Link>
              </Button>
            ) : null}
          </div>
        }
      />
    </div>
  )
}

function copyFor(access: BlockedAccess) {
  switch (access.kind) {
    case 'never-subscribed':
      return {
        tone: 'info',
        title: '当前工作空间尚未订阅标书编写智能体',
        description: '订阅在控制台完成。完成后回到这里，点「我已完成订阅」即可进入。',
        subscribeLabel: '前往订阅',
      } as const
    case 'lapsed':
      return {
        tone: 'warning',
        title: `当前工作空间的订阅${access.statusLabel}`,
        description:
          '续订在控制台完成，完成后回到这里点「我已完成订阅」。' +
          (access.dataRetentionUntil
            ? `已有标书的数据保留至 ${formatDate(access.dataRetentionUntil)}。`
            : ''),
        subscribeLabel: '前往续订',
      } as const
    case 'unknown-tier':
      return {
        tone: 'warning',
        title: `暂不支持当前订阅档位（${access.tier}）`,
        description: '本产品还不认得这个档位，为避免按错误的能力放行，暂不开放使用。请联系管理员。',
        subscribeLabel: '查看订阅',
      } as const
    case 'unavailable':
      return {
        tone: 'danger',
        title: '暂时无法确认订阅状态',
        description: '订阅服务暂时不可达，这不代表你的订阅有问题。请稍后重试。',
        subscribeLabel: null,
      } as const
  }
}
