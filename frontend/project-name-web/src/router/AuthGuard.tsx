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

export default function AuthGuard({ children }: AuthGuardProps) {
  const location = useLocation()
  const token = useAuthStore((state) => state.token)
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const currentUser = useQuery({
    queryKey: ['current-user'],
    queryFn: authApi.me,
    enabled: Boolean(token),
    retry: false,
    staleTime: 60_000,
  })

  useEffect(() => {
    if (currentUser.data) setUser(currentUser.data)
    if (currentUser.isError) clearAuth()
  }, [clearAuth, currentUser.data, currentUser.isError, setUser])

  if (!token || currentUser.isError) {
    const redirect = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  if (currentUser.isLoading && !user) {
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
