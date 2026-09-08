// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useQuery } from '@tanstack/react-query'
import { ShellBootScreen } from '@vxture/design-system'

import { authApi } from '@/api/modules/auth'
import { useAuthStore } from '@/stores/auth'

interface AuthGuardProps {
  children: React.ReactNode
}

/**
 * 是否已登录，由<b>服务端</b>裁定，不由 localStorage 里有没有一个字符串裁定。
 *
 * 这不只是重构：平台 RP 会话装在 HttpOnly cookie 里，浏览器<b>读不到它</b>，
 * 所以「本地有没有 token」根本回答不了这个问题——OIDC 登录回来后 localStorage
 * 是空的，而用户确实已经登录了。反过来，一个过期的本地 token 会让守卫放行，
 * 然后每个业务请求各自 401 一次。
 *
 * 代价是每次进受保护路由要问一次服务端（react-query 缓存 60 秒）。
 */
export default function AuthGuard({ children }: AuthGuardProps) {
  const location = useLocation()
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const currentUser = useQuery({
    queryKey: ['current-user'],
    queryFn: authApi.me,
    retry: false,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (currentUser.data) setUser(currentUser.data)
    if (currentUser.isError) clearAuth()
  }, [clearAuth, currentUser.data, currentUser.isError, setUser])

  if (currentUser.isError) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  if (currentUser.isPending) {
    return (
      <ShellBootScreen
        label="TenderAgent"
        description="正在确认登录状态"
        delayMs={250}
      />
    )
  }

  const resolvedUser = currentUser.data ?? user
  if (location.pathname.startsWith('/console') && !resolvedUser?.admin) {
    return <Navigate to="/403" replace />
  }
  return <>{children}</>
}
