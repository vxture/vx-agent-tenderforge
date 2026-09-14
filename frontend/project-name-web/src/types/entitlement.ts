// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

/** `GET /api/entitlement` 的形状。能力集是产品对档位的解读；订阅事实原样透出。 */
export interface EntitlementView {
  product: string
  workspaceId: string | null
  allowsProductSurface: boolean
  allowsDataPlane: boolean
  capabilities: string[]
  subscription: {
    status: string | null
    tier: string | null
    bundled: boolean
    trialEndsAt: string | null
    currentPeriodEnd: string | null
    cancelAtPeriodEnd: boolean
    dataRetentionUntil: string | null
    tierKnown: boolean
  }
  /** console 转化深链。只在用户显式点击时打开，永不自动跳转。 */
  subscribeUrl: string
  degraded: boolean
}
