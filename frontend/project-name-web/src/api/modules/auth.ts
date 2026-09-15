// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { apiRequest } from '@/api/client'
import type { CurrentUser } from '@/types/auth'

export const authApi = {
  /**
   * 由服务端裁定当前是否已登录。
   *
   * 401 不走全局跳转：这个调用本来就是在问「我登录了吗」，得到「没有」
   * 是一个正常答案。会话装在 HttpOnly cookie 里，浏览器读不到，前端无从自己判断。
   * 登录本身不是一次 fetch，而是整页跳到 /api/auth/oidc/login。
   */
  me: () => apiRequest<CurrentUser>('/api/auth/me', { onUnauthorized: 'ignore' }),
  // 登出是状态迁移，走具名路由而不是 DELETE 一个叫 session 的资源（通则 B-4）。
  // 回显平台登出端点地址，浏览器要顶层导航过去才能结束账户中心会话（通则 C1）。
  logout: () => apiRequest<LogoutResult>('/api/auth/logout', { method: 'POST' }),
}

/** `logoutUrl` 为 null：替身身份、配置不全或身份服务不可达，退回站内登录页。 */
export type LogoutResult = { logoutUrl: string | null }
