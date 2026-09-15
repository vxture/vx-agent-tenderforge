// @vitest-environment happy-dom
// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'

import { ApiError } from '@/api/client'
import { SIGNED_OUT_COOKIE } from '@/app/auth/signed-out-marker'
import { useAuthStore } from '@/stores/auth'
import { GateProviders } from '@/test/gate-providers'
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
  orgName: null,
  workspaceName: null,
  admin: false,
  consoleProfileUrl: null,
}

const unauthenticated = () => new ApiError('未登录', 401, 'UNAUTHENTICATED', false)

function Where() {
  const location = useLocation()
  return <p>{`at ${location.pathname}${location.search}`}</p>
}

function tree(path: string, content: ReactNode, client: QueryClient) {
  return (
    <GateProviders>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/login" element={<Where />} />
            <Route path="/403" element={<Where />} />
            <Route path="*" element={<AuthGuard>{content}</AuthGuard>} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </GateProviders>
  )
}

function renderAt(path: string, content: ReactNode = <p>受保护内容</p>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const result = render(tree(path, content, client))
  return { ...result, rerenderSame: () => result.rerender(tree(path, content, client)) }
}

const markerPresent = () => document.cookie.split('; ').some((entry) => entry.startsWith(`${SIGNED_OUT_COOKIE}=1`))

describe('AuthGuard', () => {
  beforeEach(() => {
    auth.me.mockReset()
    useAuthStore.getState().clearAuth()
  })

  afterEach(() => {
    document.cookie = `${SIGNED_OUT_COOKIE}=; path=/; max-age=0`
  })

  it('服务端确认已登录才放行，并把用户资料写进本地缓存', async () => {
    auth.me.mockResolvedValue(planner)

    renderAt('/planner/writing')

    expect(await screen.findByText('受保护内容')).toBeTruthy()
    expect(useAuthStore.getState().user?.id).toBe('usr-1')
  })

  it('没有会话时就地渲染引导页，不跳走；「登录」带上原来的路径与查询串', async () => {
    auth.me.mockRejectedValue(unauthenticated())

    renderAt('/planner/bids?cursor=abc')

    expect(await screen.findByRole('heading', { name: '欢迎使用' })).toBeTruthy()
    expect(screen.queryByText(/^at /)).toBeNull()
    expect(screen.queryByText('受保护内容')).toBeNull()
    expect(screen.getByRole('link', { name: '登录' }).getAttribute('href')).toBe(
      '/api/auth/oidc/login?returnTo=%2Fplanner%2Fbids%3Fcursor%3Dabc'
    )
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

    expect(await screen.findByRole('heading', { name: '欢迎使用' })).toBeTruthy()
    // 只看终态不够（反证实测）：守卫若先按本地资料放行、清掉残留后再换页，终态一样是引导页，
    // 但受保护内容已经渲染过一次——那一次足够把上一个人的标书列表请求发出去。
    expect(mounted).not.toHaveBeenCalled()
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('刚退出的人读到便条看到「已退出登录」，便条随即清掉；守卫重渲染后仍是确认页，不中途换成引导页', async () => {
    document.cookie = `${SIGNED_OUT_COOKIE}=1; path=/`
    auth.me.mockRejectedValue(unauthenticated())

    const view = renderAt('/')

    expect(await screen.findByRole('heading', { name: '已退出登录' })).toBeTruthy()
    expect(markerPresent()).toBe(false)
    view.rerenderSame()
    expect(screen.getByRole('heading', { name: '已退出登录' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: '欢迎使用' })).toBeNull()
  })

  it('便条消费掉之后，下一次访问回到引导页', async () => {
    document.cookie = `${SIGNED_OUT_COOKIE}=1; path=/`
    auth.me.mockRejectedValue(unauthenticated())
    const first = renderAt('/')
    await screen.findByRole('heading', { name: '已退出登录' })
    first.unmount()

    renderAt('/')

    expect(await screen.findByRole('heading', { name: '欢迎使用' })).toBeTruthy()
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
