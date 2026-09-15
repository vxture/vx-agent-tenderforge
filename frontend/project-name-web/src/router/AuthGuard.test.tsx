// @vitest-environment happy-dom
// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'

import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser } from '@/types/auth'

import AuthGuard from './AuthGuard'

const auth = vi.hoisted(() => ({ me: vi.fn() }))
vi.mock('@/api/modules/auth', () => ({ authApi: auth }))

const planner: CurrentUser = {
  id: 'usr-1',
  username: 'usr-1',
  displayName: '编制员',
  roleCode: 'PLANNER',
  avatarUrl: null,
  admin: false,
  consoleProfileUrl: null,
}

function Where() {
  const location = useLocation()
  return <p>{`at ${location.pathname}${location.search}`}</p>
}

function renderAt(path: string, content: ReactNode = <p>受保护内容</p>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<Where />} />
          <Route path="/403" element={<Where />} />
          <Route
            path="*"
            element={<AuthGuard>{content}</AuthGuard>}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AuthGuard', () => {
  beforeEach(() => {
    auth.me.mockReset()
    useAuthStore.getState().clearAuth()
  })

  it('服务端确认已登录才放行，并把用户资料写进本地缓存', async () => {
    auth.me.mockResolvedValue(planner)

    renderAt('/planner/writing')

    expect(await screen.findByText('受保护内容')).toBeTruthy()
    expect(useAuthStore.getState().user?.id).toBe('usr-1')
  })

  it('服务端说没登录时回登录页，并带上原来的路径与查询串', async () => {
    auth.me.mockRejectedValue(new ApiError('未登录', 401, 'UNAUTHENTICATED', false))

    renderAt('/planner/bids?cursor=abc')

    expect(await screen.findByText('at /login?redirect=%2Fplanner%2Fbids%3Fcursor%3Dabc')).toBeTruthy()
    expect(screen.queryByText('受保护内容')).toBeNull()
  })

  it('本地残留的用户资料不能代替服务端答复——会话过期时一刻也不渲染受保护内容', async () => {
    useAuthStore.getState().setUser(planner)
    auth.me.mockRejectedValue(new ApiError('会话已过期', 401, 'UNAUTHENTICATED', false))
    const mounted = vi.fn()
    function Probe() {
      mounted()
      return <p>受保护内容</p>
    }

    renderAt('/planner/writing', <Probe />)

    expect(await screen.findByText(/^at \/login/)).toBeTruthy()
    // 只看终态不够（反证实测）：守卫若先按本地资料放行、清掉残留后再跳走，终态一样是登录页，
    // 但受保护内容已经渲染过一次——那一次足够把上一个人的标书列表请求发出去。
    expect(mounted).not.toHaveBeenCalled()
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('非管理员进控制台被挡到 403', async () => {
    auth.me.mockResolvedValue(planner)

    renderAt('/console/audit-logs')

    expect(await screen.findByText('at /403')).toBeTruthy()
    expect(screen.queryByText('受保护内容')).toBeNull()
  })

  it('管理员可以进控制台', async () => {
    auth.me.mockResolvedValue({ ...planner, roleCode: 'ADMIN', admin: true })

    renderAt('/console/audit-logs')

    expect(await screen.findByText('受保护内容')).toBeTruthy()
  })
})
