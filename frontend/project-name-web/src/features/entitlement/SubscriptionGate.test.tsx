// @vitest-environment happy-dom
// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

import type { EntitlementView } from '@/types/entitlement'

import { SubscriptionGate } from './SubscriptionGate'

const api = vi.hoisted(() => ({ current: vi.fn(), refresh: vi.fn() }))
vi.mock('@/api/modules/entitlement', () => ({ entitlementApi: api }))

const SUBSCRIBE_URL = 'https://console.vxture.com/subscribe?product=tenderforge&intent=subscribe'
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

function renderGate({ standalone = false }: { standalone?: boolean } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(
    <SubscriptionGate standalone={standalone}>
      <p>产品内容</p>
    </SubscriptionGate>,
    { wrapper }
  )
}

describe('SubscriptionGate', () => {
  beforeEach(() => {
    api.current.mockReset()
    api.refresh.mockReset()
  })

  it('有生效档位时直接放行，看不到订阅页', async () => {
    api.current.mockResolvedValue(granted())

    renderGate()

    expect(await screen.findByText('产品内容')).toBeTruthy()
    expect(screen.queryByText(/尚未订阅/)).toBeNull()
  })

  it('从未订阅时停在订阅页，产品内容不渲染——生产上登录后直接进了智能体的正是这个响应', async () => {
    api.current.mockResolvedValue(view())

    renderGate()

    expect(await screen.findByText('当前工作空间尚未订阅标书编写智能体')).toBeTruthy()
    expect(screen.queryByText('产品内容')).toBeNull()
    expect(screen.getByRole('button', { name: '前往订阅' })).toBeTruthy()
    expect(screen.getByRole('button', { name: '我已完成订阅' })).toBeTruthy()
  })

  it('「前往订阅」只在点击时新开窗口，页面本身不跳走', async () => {
    api.current.mockResolvedValue(view())
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    // happy-dom 里 location.assign 与 href 赋值不会真的改掉 href，比较前后 href 验不出跳转
    // （反证实测：闸门里加一句 location.assign，旧写法照样绿）。把 location 换成记录调用的替身，
    // 并先确认替身确实生效——替身没挂上时这条断言又会变成空的。
    const original = Object.getOwnPropertyDescriptor(window, 'location')
    const attempts: string[] = []
    const trap = {
      get href() {
        return 'http://localhost:3000/planner/writing'
      },
      set href(value: string) {
        attempts.push(`href=${value}`)
      },
      assign: (url: string) => attempts.push(`assign ${url}`),
      replace: (url: string) => attempts.push(`replace ${url}`),
    }
    Object.defineProperty(window, 'location', { configurable: true, value: trap })

    try {
      expect(window.location).toBe(trap)
      renderGate()
      await userEvent.click(await screen.findByRole('button', { name: '前往订阅' }))

      expect(open).toHaveBeenCalledWith(SUBSCRIBE_URL, '_blank', 'noopener,noreferrer')
      expect(attempts).toEqual([])
    } finally {
      if (original) Object.defineProperty(window, 'location', original)
      else Reflect.deleteProperty(window, 'location')
      open.mockRestore()
    }
  })

  it('订阅已失效时说「续订」并带出数据保留期，而不是「开始订阅」', async () => {
    api.current.mockResolvedValue(
      view({ subscription: { status: 'expired', dataRetentionUntil: '2026-12-31T00:00:00Z' } })
    )

    renderGate()

    expect(await screen.findByText('当前工作空间的订阅已到期')).toBeTruthy()
    expect(screen.getByRole('button', { name: '前往续订' })).toBeTruthy()
    expect(screen.getByText(/已有标书的数据保留至/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: '前往订阅' })).toBeNull()
  })

  it('不认得的档位不放行，并把档位值说出来', async () => {
    api.current.mockResolvedValue(
      view({ allowsProductSurface: true, capabilities: [], subscription: { status: 'active', tier: 'platinum' } })
    )

    renderGate()

    expect(await screen.findByText('暂不支持当前订阅档位（platinum）')).toBeTruthy()
    expect(screen.queryByText('产品内容')).toBeNull()
  })

  it('平台没答上来时说「暂时无法确认」，不给订阅入口，只给重试', async () => {
    api.current.mockResolvedValue(view({ unavailable: true }))

    renderGate()

    expect(await screen.findByText('暂时无法确认订阅状态')).toBeTruthy()
    expect(screen.queryByText(/尚未订阅/)).toBeNull()
    expect(screen.queryByRole('button', { name: '前往订阅' })).toBeNull()
    expect(screen.getByRole('button', { name: '重试' })).toBeTruthy()
  })

  it('权益接口本身请求失败时同样说「暂时无法确认」，不当成没订阅', async () => {
    api.current.mockRejectedValue(new Error('network down'))

    renderGate()

    expect(await screen.findByText('暂时无法确认订阅状态')).toBeTruthy()
    expect(screen.queryByText(/尚未订阅/)).toBeNull()
  })

  it('「我已完成订阅」走刷新接口，平台确认后当场放行，不必刷新页面', async () => {
    api.current.mockResolvedValue(view())
    api.refresh.mockResolvedValue(granted())

    renderGate()
    await userEvent.click(await screen.findByRole('button', { name: '我已完成订阅' }))

    expect(api.refresh).toHaveBeenCalledTimes(1)
    await waitFor(() => expect(screen.getByText('产品内容')).toBeTruthy())
  })

  it('全屏标书页没有页头，停下时给一条回工作台的路', async () => {
    api.current.mockResolvedValue(view())

    renderGate({ standalone: true })

    const back = await screen.findByRole('link', { name: '返回工作台' })
    expect(back.getAttribute('href')).toBe('/')
  })

  it('主布局内不重复给回工作台的路（页头已经有导航）', async () => {
    api.current.mockResolvedValue(view())

    renderGate()

    await screen.findByText('当前工作空间尚未订阅标书编写智能体')
    expect(screen.queryByRole('link', { name: '返回工作台' })).toBeNull()
  })
})
