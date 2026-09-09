// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError, apiRequest, isRejection, REJECTION_CODES } from './client'

/**
 * 前端侧的 X-1 封套契约。
 *
 * 这一组保护的是「拿到失败之后我们怎么读它」。读错了不会崩：
 * `retryable` 读成 undefined 会让重试逻辑安静地退回按状态码猜，
 * 而那正是这个字段存在的全部意义。
 */

/**
 * 取出这次请求失败时抛出的 ApiError。
 *
 * 顺带断言了两件事：请求确实失败了（成功会在这里显式报错，而不是让后续断言
 * 对着一个 undefined 静默通过），以及抛出来的确实是 ApiError 而不是别的什么
 * ——后者意味着解包路径在中途就崩了，那和「拿到了一个格式不对的封套」是两回事。
 */
async function failureOf(request: Promise<unknown>): Promise<ApiError> {
  try {
    await request
  } catch (error) {
    if (error instanceof ApiError) return error
    throw error
  }
  throw new Error('预期请求失败，但它成功了')
}

const jsonResponse = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })

describe('apiRequest', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    // apiRequest 无条件读一次 localStorage 取 token，所以即使 authenticated:false
    // 也需要 window 存在。这里给一个最小替身而不是引 jsdom：
    // 这几条是纯解包逻辑的用例，为它们背一整个 DOM 实现只会让套件变慢，
    // 而且会把「这段代码依赖浏览器全局」这个事实藏起来。
    vi.stubGlobal('window', {
      localStorage: { getItem: () => null, removeItem: () => undefined },
      location: { pathname: '/', search: '', href: '' },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('成功响应直接是载荷，没有外层信封', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, { id: 'bid-1', title: '标书' }))

    await expect(apiRequest('/api/bids/bid-1', { authenticated: false })).resolves.toEqual({
      id: 'bid-1',
      title: '标书',
    })
  })

  it('列表响应是裸数组，不再从 data 里剥一层', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(200, [{ id: 'a' }, { id: 'b' }]))

    await expect(apiRequest('/api/bids', { authenticated: false })).resolves.toHaveLength(2)
  })

  it('204 不解析空体，也不返回一个占位对象', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }))

    await expect(apiRequest('/api/auth/logout', { method: 'POST', authenticated: false }))
      .resolves.toBeUndefined()
  })

  it('失败时把封套四个字段原样带进 ApiError', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(400, {
        code: 'REQUEST_VALIDATION_FAILED',
        message: 'title：长度必须为2至160个字符',
        retryable: false,
        field: 'title',
      })
    )

    const error = await failureOf(apiRequest('/api/bids', { authenticated: false }))

    expect(error.code).toBe('REQUEST_VALIDATION_FAILED')
    expect(error.retryable).toBe(false)
    expect(error.field).toBe('title')
    expect(error.status).toBe(400)
  })

  /**
   * 这一条是整组里最重要的：被调方说 false 就是 false。
   *
   * 商业配额上限确实会以 429 到达，而它重试无益——正确动作是挂起任务。
   * 一个「429 就重试」的客户端会淹掉自己的队列，并且永远不知道为什么。
   */
  it('封套里的 retryable 压过按状态码的推断', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(429, { code: 'QUOTA_EXCEEDED', message: '配额耗尽', retryable: false })
    )

    const error = await failureOf(apiRequest('/api/bids', { authenticated: false }))

    expect(error.retryable).toBe(false)
  })

  it('传输层失败读不到封套时才退回按状态码猜', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('<html>502</html>', { status: 502 }))

    const error = await failureOf(apiRequest('/api/bids', { authenticated: false }))

    expect(error.code).toBe('TRANSPORT_ERROR')
    expect(error.retryable).toBe(true)
  })

  it('4xx 且读不到封套时不猜成可重试', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('', { status: 400 }))

    const error = await failureOf(apiRequest('/api/bids', { authenticated: false }))

    expect(error.retryable).toBe(false)
  })
})

describe('401 的两种处置', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
    vi.stubGlobal('window', {
      localStorage: { getItem: () => 'stale-token', removeItem: () => undefined },
      location: { pathname: '/planner/bids', search: '', href: '' },
    })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  /**
   * 默认：401 视为会话失效，跳登录页。
   */
  it('默认在 401 时跳转登录页', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { code: 'X', message: 'y', retryable: false }))

    await failureOf(apiRequest('/api/bids'))

    expect(window.location.href).toContain('/login?redirect=')
  })

  /**
   * 鉴权守卫要的是相反的行为：它本来就是在问「我登录了吗」，
   * 得到「没有」是一个正常答案。走全局跳转会把首次访问变成一次整页刷新，
   * 而软跳转本可以在应用内完成。
   */
  it('onUnauthorized: ignore 时把 401 原样交回调用方', async () => {
    vi.mocked(fetch).mockResolvedValue(jsonResponse(401, { code: 'X', message: 'y', retryable: false }))

    const error = await failureOf(apiRequest('/api/auth/me', { onUnauthorized: 'ignore' }))

    expect(error.status).toBe(401)
    expect(window.location.href).toBe('')
  })
})

describe('拒绝码词表', () => {
  it('四个平台同义码加舰队约定的限流码，拼写被钉住', () => {
    expect(REJECTION_CODES).toEqual([
      'NOT_ENTITLED',
      'POLICY_DENIED',
      'APPROVAL_REQUIRED',
      'QUOTA_EXCEEDED',
      'RATE_LIMITED',
    ])
  })

  it('isRejection 只认这五个，不把普通业务失败误判成权益问题', () => {
    expect(isRejection(new ApiError('x', 402, 'NOT_ENTITLED', false))).toBe(true)
    expect(isRejection(new ApiError('x', 404, 'BID_NOT_FOUND', false))).toBe(false)
    expect(isRejection(new Error('boom'))).toBe(false)
  })
})
