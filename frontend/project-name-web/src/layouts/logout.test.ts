// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it, vi } from 'vitest'

import { logoutAndLeave } from './logout'

/**
 * 退出之后去哪。
 *
 * 去错地方不会报错：跳回 `/login` 看起来就是「退出成功」，只有想换账号的人才会发现
 * 账户中心的会话还在。所以这一组断言的就是那个去处。
 */
describe('logoutAndLeave', () => {
  const END_SESSION =
    'https://accounts.vxture.com/oidc/end_session?client_id=tenderforge' +
    '&post_logout_redirect_uri=https%3A%2F%2Ftenderforge.vxture.com%2F'

  it('跟随服务端回显的平台登出端点', async () => {
    const go = vi.fn()
    const clearAuth = vi.fn()

    await logoutAndLeave({ logout: async () => ({ logoutUrl: END_SESSION }), clearAuth, go })

    expect(go).toHaveBeenCalledExactlyOnceWith(END_SESSION)
    expect(clearAuth).toHaveBeenCalledOnce()
  })

  it('没有登出地址时回根路径——那里读到已退出便条显示确认页，而不是回以「登录」', async () => {
    const go = vi.fn()

    await logoutAndLeave({ logout: async () => ({ logoutUrl: null }), clearAuth: vi.fn(), go })

    expect(go).toHaveBeenCalledExactlyOnceWith('/')
  })

  it('登出请求失败时仍清掉本地资料并离开', async () => {
    const go = vi.fn()
    const clearAuth = vi.fn()
    const failure = new Error('network down')

    await expect(
      logoutAndLeave({ logout: async () => Promise.reject(failure), clearAuth, go }),
    ).rejects.toBe(failure)

    expect(clearAuth).toHaveBeenCalledOnce()
    expect(go).toHaveBeenCalledExactlyOnceWith('/')
  })
})
