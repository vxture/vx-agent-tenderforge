// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { useQuery } from '@tanstack/react-query'

import { entitlementApi } from '@/api/modules/entitlement'

export const entitlementKeys = {
  current: ['entitlement'] as const,
}

/**
 * 当前工作空间的权益视图。订阅闸门、页头徽标与被拒后的订阅引导共用这一份缓存。
 *
 * staleTime 与服务端 45 秒缓存同量级：更短只是重复拉同一份答案，
 * 更长会让刚付完钱回来的人多看一会儿旧状态。
 *
 * 窗口重新聚焦时刷新（全局默认关着）：订阅在 console 的另一个标签页里完成，
 * 用户切回来的那一下正是该重新问的时候。
 */
export const useEntitlementQuery = () =>
  useQuery({
    queryKey: entitlementKeys.current,
    queryFn: entitlementApi.current,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })
