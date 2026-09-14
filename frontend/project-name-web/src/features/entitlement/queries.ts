// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { useQuery } from '@tanstack/react-query'

import { entitlementApi } from '@/api/modules/entitlement'

export const entitlementKeys = {
  current: ['entitlement'] as const,
}

/**
 * 当前工作空间的权益视图。只在需要时（被拒绝后渲染订阅引导）才发起。
 *
 * staleTime 与服务端 45 秒缓存同量级：更短只是重复拉同一份答案，
 * 更长会让刚付完钱回来的人多看一会儿旧状态。
 */
export const useEntitlementQuery = () =>
  useQuery({
    queryKey: entitlementKeys.current,
    queryFn: entitlementApi.current,
    staleTime: 30_000,
  })
