// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { accessState, subscriptionBadgeLabel, tierLabel } from './access'

import type { EntitlementView } from '../../types/entitlement'

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
  subscribeUrl: 'https://console.vxture.com/subscribe?product=tenderforge&intent=subscribe',
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

const ALL = ['AI_GENERATION', 'ASSET_LIBRARY', 'BID_AUTHORING', 'CONSISTENCY_REVIEW', 'DOCUMENT_EXPORT']

describe('accessState', () => {
  it('从未订阅的工作空间停在订阅页——正是生产上登录后直接进了智能体的那个响应', () => {
    expect(accessState(view())).toEqual({ kind: 'never-subscribed' })
  })

  it('有生效档位且能力非空才放行，并带出档位展示名', () => {
    const access = accessState(
      view({
        allowsProductSurface: true,
        capabilities: ALL,
        subscription: { status: 'active', tier: 'pro', tierKnown: true },
      })
    )
    expect(access).toEqual({ kind: 'granted', tier: 'pro', tierLabel: '专业版', trialEndsAt: null })
  })

  it('试用中放行，并带出试用截止时间', () => {
    const access = accessState(
      view({
        allowsProductSurface: true,
        capabilities: ALL,
        subscription: { status: 'trialing', tier: 'starter', trialEndsAt: '2026-10-01T00:00:00Z' },
      })
    )
    expect(access).toMatchObject({ kind: 'granted', trialEndsAt: '2026-10-01T00:00:00Z' })
  })

  it('失效的订阅说「续订」而不是「开始订阅」，并带出数据保留期', () => {
    const access = accessState(
      view({ subscription: { status: 'expired', dataRetentionUntil: '2026-12-31T00:00:00Z' } })
    )
    expect(access).toEqual({
      kind: 'lapsed',
      status: 'expired',
      statusLabel: '已到期',
      dataRetentionUntil: '2026-12-31T00:00:00Z',
    })
  })

  it('不认得的档位不放行：界面放进去了、每个按钮都 403 是更坏的体验', () => {
    const access = accessState(
      view({ allowsProductSurface: true, capabilities: [], subscription: { status: 'active', tier: 'platinum' } })
    )
    expect(access).toEqual({ kind: 'unknown-tier', tier: 'platinum' })
  })

  it('只有捆绑覆盖时不开界面（捆绑只开数据面）', () => {
    const access = accessState(view({ allowsDataPlane: true, subscription: { bundled: true } }))
    expect(access).toEqual({ kind: 'never-subscribed' })
  })

  it('没问到权益时说「暂时无法确认」，不说「尚未订阅」', () => {
    expect(accessState(view({ unavailable: true }))).toEqual({ kind: 'unavailable' })
    expect(accessState(undefined)).toEqual({ kind: 'unavailable' })
  })
})

describe('tierLabel 与徽标', () => {
  it('五档有展示名，没见过的档位原样显示', () => {
    expect(['free', 'starter', 'pro', 'business', 'enterprise'].map(tierLabel)).toEqual([
      '免费版',
      '入门版',
      '专业版',
      '商务版',
      '企业版',
    ])
    expect(tierLabel('platinum')).toBe('platinum')
  })

  it('徽标对每种状态说出不同的话', () => {
    expect(subscriptionBadgeLabel({ kind: 'never-subscribed' })).toBe('未订阅')
    expect(subscriptionBadgeLabel({ kind: 'unavailable' })).toBe('订阅状态暂不可知')
    expect(
      subscriptionBadgeLabel({ kind: 'granted', tier: 'pro', tierLabel: '专业版', trialEndsAt: null })
    ).toBe('专业版')
  })
})
