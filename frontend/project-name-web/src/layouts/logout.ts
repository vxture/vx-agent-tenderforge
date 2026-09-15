// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { LogoutResult } from '@/api/modules/auth'

type LogoutDeps = {
  logout: () => Promise<LogoutResult | undefined>
  clearAuth: () => void
  go: (url: string) => void
}

/**
 * 退出登录并离开当前页。
 *
 * 去处由服务端决定：有 `logoutUrl` 就顶层导航到平台登出端点——账户中心的会话只有
 * 这样才会结束，人随后回到登记的回跳地址；没有就回站内登录页。
 * 此前直接跳 `/login`，本地会话删了、账户中心的会话还在，再点登录会被静默送回来，
 * 换不了账号。
 *
 * 请求失败时照样清掉本地资料并离开：用户点的是「退出」，停在原页比退错地方更糟。
 */
export async function logoutAndLeave({ logout, clearAuth, go }: LogoutDeps): Promise<void> {
  let target = '/login'
  try {
    const result = await logout()
    if (result?.logoutUrl) target = result.logoutUrl
  } finally {
    clearAuth()
    go(target)
  }
}
