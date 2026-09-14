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
  logout: () => apiRequest<void>('/api/auth/logout', { method: 'POST' }),
}
