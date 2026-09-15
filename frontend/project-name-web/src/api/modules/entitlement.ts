// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { apiRequest } from '@/api/client'
import type { EntitlementView } from '@/types/entitlement'

export const entitlementApi = {
  current: () => apiRequest<EntitlementView>('/api/entitlement'),
  /** 先驱逐本工作空间的服务端权益缓存再问平台。「我已完成订阅」用它。 */
  refresh: () => apiRequest<EntitlementView>('/api/entitlement/refresh', { method: 'POST' }),
}
