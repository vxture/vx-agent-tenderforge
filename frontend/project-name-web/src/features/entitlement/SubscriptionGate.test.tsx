// @vitest-environment happy-dom
// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import { useAuthStore } from '@/stores/auth'
import { GateProviders } from '@/test/gate-providers'
import type { CurrentUser } from '@/types/auth'
import type { EntitlementView } from '@/types/entitlement'

import { SubscriptionGate } from './SubscriptionGate'

const api = vi.hoisted(() => ({ current: vi.fn(), refresh: vi.fn() }))
vi.mock('@/api/modules/entitlement', () => ({ entitlementApi: api }))

// 官网定价页（owner 2026-09-16）：套餐在那里发布；服务端按请求语言拼好，前端原样打开。
const SUBSCRIBE_URL = 'https://vxture.com/zh-CN/pricing?product=tenderforge'
const ALL = ['AI_GENERATION', 'ASSET_LIBRARY', 'BID_AUTHORING', 'CONSISTENCY_REVIEW', 'DOCUMENT_EXPORT']

type Overrides = Omit<Partial<EntitlementView>, 'subscription'> & {
  subscription?: Partial<EntitlementView['subscription']>
}

/** 默认值取自 2026-09-15 生产上一个未订阅工作空间的真实响应。 */
const view = ({ subscription, ...rest }: Overrides = {}): EntitlementView => ({
  product: 'tenderforge',
  workspaceId: 'ws-1',
  allowsProductSurface: false,
  allowsDataPlane: false,
  capabilities: [],
  subscribeUrl: SUBSCRIBE_URL,
  degraded: false,
  unavailable: false,
  ...rest,
  subscription: {
    status: null,
    tier: null,
    bundled: false,
    trialEndsAt: null,
    currentPeriodEnd: null,
    cancelAtPeriodEnd: false,
    dataRetentionUntil: null,
    tierKnown: false,
    ...subscription,
  },
})

const granted = () =>
  view({
    allowsProductSurface: true,
    capabilities: ALL,
    subscription: { status: 'active', tier: 'pro', tierKnown: true },
  })

function renderGate() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <GateProviders>
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    </GateProviders>
  )
  return render(
    <SubscriptionGate>
      <p>产品内容</p>
    </SubscriptionGate>,
    { wrapper }
  )
}

/** 名字取自平台 access token：name、active_org_name、active_workspace_name。 */
const signedIn = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 'usr-1',
  username: 'usr-1',
  displayName: '编制员小王',
  roleCode: 'PLANNER',
  avatarUrl: null,
  orgName: '华东设计院',
  workspaceName: '投标一部',
  email: 'wang@example.com',
  phone: '+8613800001234',
  admin: false,
  consoleProfileUrl: null,
  ...overrides,
})

describe('SubscriptionGate', () => {
  beforeEach(() => {
    api.current.mockReset()
    api.refresh.mockReset()
    useAuthStore.getState().setUser(signedIn())
  })

  it('有生效档位时直接放行，看不到门禁页', async () => {
    api.current.mockResolvedValue(granted())

    renderGate()

    expect(await screen.findByText('产品内容')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: '当前工作区未订阅' })).toBeNull()
  })

  it('从未订阅时停在门禁页，产品内容不渲染——生产上登录后直接进了智能体的正是这个响应', async () => {
    api.current.mockResolvedValue(view())

    renderGate()

    expect(await screen.findByRole('heading', { name: '当前工作区未订阅' })).toBeTruthy()
    expect(screen.queryByText('产品内容')).toBeNull()
    expect(screen.getByText('未订阅')).toBeTruthy()
    // 只有产品能给的两件事：谁在登录、在哪个工作区——说的是平台签发的名字，不是标识；不带标签（owner 2026-09-16）。
    const identity = within(screen.getByRole('group', { name: '登录身份与当前工作区' }))
    expect(identity.getByText('编制员小王')).toBeTruthy()
    // 人名下面是手机号（owner 2026-09-16），与单位一侧两行对齐。
    expect(identity.getByText('138 0000 1234')).toBeTruthy()
    // 组织一行、工作区一行，各自完整，不拼成一串去被截断。
    expect(identity.getByText('华东设计院')).toBeTruthy()
    expect(identity.getByText('投标一部')).toBeTruthy()
    expect(screen.queryByText('登录身份')).toBeNull()
    expect(screen.queryByText('usr-1')).toBeNull()
  })

  it('会话里没有工作区名时（名字上线之前建立的会话）第二行显示兜底文案，不拿标识凑数', async () => {
    useAuthStore.getState().setUser(signedIn({ orgName: null, workspaceName: null }))
    api.current.mockResolvedValue(view())

    renderGate()

    await screen.findByRole('heading', { name: '当前工作区未订阅' })
    const identity = within(screen.getByRole('group', { name: '登录身份与当前工作区' }))
    expect(identity.getByText('当前工作区')).toBeTruthy()
    expect(identity.queryByText('usr-1')).toBeNull()
  })

  it('「前往订阅」是新开窗口的链接，指向服务端给的深链；页面本身不跳走（通则 C2）', async () => {
    api.current.mockResolvedValue(view())

    renderGate()

    const subscribe = await screen.findByRole('link', { name: '前往订阅' })
    expect(subscribe.getAttribute('href')).toBe(SUBSCRIBE_URL)
    expect(subscribe.getAttribute('target')).toBe('_blank')
    expect(subscribe.getAttribute('rel')).toBe('noopener noreferrer')
  })

  it('退出登录是真实的表单 POST，不依赖脚本', async () => {
    api.current.mockResolvedValue(view())

    renderGate()

    const signOut = await screen.findByRole('button', { name: '退出登录' })
    const form = signOut.closest('form')
    expect(form?.getAttribute('method')).toBe('post')
    expect(form?.getAttribute('action')).toBe('/api/auth/logout')
    expect(signOut.getAttribute('type')).toBe('submit')
  })

  it('订阅已失效时说「续订」并带出数据保留期，而不是「开始订阅」', async () => {
    api.current.mockResolvedValue(
      view({ subscription: { status: 'expired', dataRetentionUntil: '2026-12-31T00:00:00Z' } })
    )

    renderGate()

    expect(await screen.findByRole('heading', { name: '当前工作区的订阅已失效' })).toBeTruthy()
    expect(screen.getByText('订阅已到期')).toBeTruthy()
    expect(screen.getByRole('link', { name: '前往续订' })).toBeTruthy()
    expect(screen.getByText(/已有标书的数据保留至/)).toBeTruthy()
    expect(screen.queryByRole('link', { name: '前往订阅' })).toBeNull()
  })

  it('不认得的档位不放行，并把档位值说出来', async () => {
    api.current.mockResolvedValue(
      view({ allowsProductSurface: true, capabilities: [], subscription: { status: 'active', tier: 'platinum' } })
    )

    renderGate()

    expect(await screen.findByRole('heading', { name: '暂不支持当前订阅档位' })).toBeTruthy()
    expect(screen.getByText(/platinum/)).toBeTruthy()
    expect(screen.queryByText('产品内容')).toBeNull()
  })

  it('平台没答上来时说「暂时无法确认」，不给订阅入口，只给重试——重试走刷新接口', async () => {
    api.current.mockResolvedValue(view({ unavailable: true }))
    api.refresh.mockResolvedValue(granted())

    renderGate()

    expect(await screen.findByRole('heading', { name: '暂时无法确认订阅状态' })).toBeTruthy()
    expect(screen.queryByRole('link', { name: '前往订阅' })).toBeNull()
    await userEvent.click(screen.getByRole('button', { name: '重试' }))

    expect(api.refresh).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(screen.getByText('产品内容')).toBeTruthy())
  })

  it('权益接口本身请求失败时同样说「暂时无法确认」，不当成没订阅', async () => {
    api.current.mockRejectedValue(new Error('network down'))

    renderGate()

    expect(await screen.findByRole('heading', { name: '暂时无法确认订阅状态' })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: '当前工作区未订阅' })).toBeNull()
  })

  it('从控制台订阅完切回这个标签页：走刷新接口，平台确认后当场放行，不必刷新页面', async () => {
    api.current.mockResolvedValue(view())
    api.refresh.mockResolvedValue(granted())

    renderGate()
    await screen.findByRole('heading', { name: '当前工作区未订阅' })
    fireEvent(window, new Event('focus'))

    await waitFor(() => expect(api.refresh).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(screen.getByText('产品内容')).toBeTruthy())
  })

  it('放行之后切回标签页不再额外刷新', async () => {
    api.current.mockResolvedValue(granted())

    renderGate()
    await screen.findByText('产品内容')
    fireEvent(window, new Event('focus'))

    expect(api.refresh).not.toHaveBeenCalled()
  })
})
