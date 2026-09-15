// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { EntitlementView } from '../../types/entitlement'

/**
 * 进入产品界面之前的判定结果。
 *
 * 公式来自通则 C2：界面门控 `tier != null`（服务端算好的 `allowsProductSurface`），**本地不放宽**。
 * 在此之上只收紧一处：档位本产品不认得（能力集为空）时也不放行——与服务端命令判定一致，
 * 否则界面放进去了、每个按钮都 403。
 *
 * 五种结果对应五种出路，混了就会把人带错地方：对从没买过的人说「续订」，对刚过期的人说
 * 「开始订阅」，或者平台抖一下就对付了钱的人说「你还没订阅」。
 */
export type AccessState =
  | { kind: 'granted'; tier: string; tierLabel: string; trialEndsAt: string | null }
  | { kind: 'never-subscribed' }
  | { kind: 'lapsed'; status: string; statusLabel: string; dataRetentionUntil: string | null }
  | { kind: 'unknown-tier'; tier: string }
  | { kind: 'unavailable' }

const TIER_LABELS: Record<string, string> = {
  free: '免费版',
  starter: '入门版',
  pro: '专业版',
  business: '商务版',
  enterprise: '企业版',
}

const STATUS_LABELS: Record<string, string> = {
  expired: '已到期',
  cancelled: '已取消',
  suspended: '已暂停',
  overdue: '已逾期',
}

/** 档位的展示名。没见过的档位原样显示——它值得被看见，而不是被翻译成某个已知档。 */
export function tierLabel(tier: string): string {
  return TIER_LABELS[tier.trim().toLowerCase()] ?? tier
}

export function accessState(view: EntitlementView | undefined): AccessState {
  if (!view || view.unavailable) return { kind: 'unavailable' }
  const { subscription } = view
  if (view.allowsProductSurface && subscription.tier) {
    if (view.capabilities.length === 0) return { kind: 'unknown-tier', tier: subscription.tier }
    return {
      kind: 'granted',
      tier: subscription.tier,
      tierLabel: tierLabel(subscription.tier),
      trialEndsAt: subscription.status === 'trialing' ? subscription.trialEndsAt : null,
    }
  }
  // 只有捆绑覆盖、没有直接购买的空间也落在这里：捆绑只开数据面，不开界面（通则门控公式）。
  if (subscription.status === null) return { kind: 'never-subscribed' }
  const status = subscription.status
  return {
    kind: 'lapsed',
    status,
    statusLabel: STATUS_LABELS[status.trim().toLowerCase()] ?? status,
    dataRetentionUntil: subscription.dataRetentionUntil,
  }
}

/** 页头账号菜单里的订阅徽标。 */
export function subscriptionBadgeLabel(access: AccessState): string {
  switch (access.kind) {
    case 'granted':
      return access.trialEndsAt
        ? `${access.tierLabel} · 试用至 ${formatDate(access.trialEndsAt)}`
        : access.tierLabel
    case 'never-subscribed':
      return '未订阅'
    case 'lapsed':
      return `订阅${access.statusLabel}`
    case 'unknown-tier':
      return `未识别档位 ${access.tier}`
    case 'unavailable':
      return '订阅状态暂不可知'
  }
}

export function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN')
}
