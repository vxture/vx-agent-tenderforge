// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { Banner, Button } from '@vxture/design-system'

import { useEntitlementQuery } from './queries'
import type { RejectionNotice } from './rejection'

/**
 * 命令被权益拒绝时的引导。
 *
 * 深链来自 `GET /api/entitlement`（intent 由服务端按订阅状态选），
 * 只在用户**显式点击**时新开窗口打开——通则 C2 与 EntitlementController 注释都要求
 * 永不自动跳转：自动跳走会让用户丢掉正在编辑的内容，也看不到为什么被拒。
 * 深链还没取到时不渲染按钮，而不是给一个空链接。
 */
export function SubscriptionNotice({ notice }: { notice: RejectionNotice }) {
  const entitlement = useEntitlementQuery()
  const subscribeUrl = entitlement.data?.subscribeUrl
  return (
    <Banner
      tone="warning"
      title={notice.title}
      description={notice.description}
      action={
        subscribeUrl ? (
          <Button
            variant="outline"
            size="sm"
            onClick={() => window.open(subscribeUrl, '_blank', 'noopener,noreferrer')}
          >
            {notice.actionLabel}
          </Button>
        ) : undefined
      }
    />
  )
}
