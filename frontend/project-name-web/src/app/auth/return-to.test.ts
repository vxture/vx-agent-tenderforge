// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { loginHref, safeReturnTo } from './return-to'

describe('safeReturnTo', () => {
  it('站内路径原样保留', () => {
    expect(safeReturnTo('/planner/bids?cursor=abc')).toBe('/planner/bids?cursor=abc')
  })

  it('协议相对的站外地址、非路径、回到登录页本身，一律回根路径', () => {
    for (const unsafe of ['//evil.example', 'https://evil.example', 'planner', '/login?redirect=/x', null, '']) {
      expect(safeReturnTo(unsafe)).toBe('/')
    }
  })
})

describe('loginHref', () => {
  it('引导页就地渲染时带上当前地址', () => {
    expect(loginHref({ pathname: '/planner/bids', search: '?cursor=abc' })).toBe(
      '/api/auth/oidc/login?returnTo=%2Fplanner%2Fbids%3Fcursor%3Dabc'
    )
  })

  it('旧的 /login?redirect= 链接仍然认，但只认站内地址', () => {
    expect(loginHref({ pathname: '/login', search: '?redirect=%2Fplanner%2Fassets' })).toBe(
      '/api/auth/oidc/login?returnTo=%2Fplanner%2Fassets'
    )
    expect(loginHref({ pathname: '/login', search: '?redirect=%2F%2Fevil.example' })).toBe(
      '/api/auth/oidc/login?returnTo=%2F'
    )
  })
})
