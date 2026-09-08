// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { apiRequest } from '@/api/client'
import type { CurrentUser, LoginResult } from '@/types/auth'

export const authApi = {
  login: (username: string, password: string) =>
    apiRequest<LoginResult>('/api/auth/login', {
      method: 'POST',
      body: { username, password },
      authenticated: false,
    }),
  /**
   * 由服务端裁定当前是否已登录。
   *
   * 401 不走全局跳转：这个调用本来就是在问「我登录了吗」，得到「没有」
   * 是一个正常答案。它同时覆盖两种会话——平台 RP 的 cookie 与本地口令的
   * Bearer——因为浏览器读不到 HttpOnly cookie，前端无从自己判断。
   */
  me: () => apiRequest<CurrentUser>('/api/auth/me', { onUnauthorized: 'ignore' }),
  // 登出是状态迁移，走具名路由而不是 DELETE 一个叫 session 的资源（通则 B-4）。
  logout: () => apiRequest<void>('/api/auth/logout', { method: 'POST' }),
}

export const accountApi = {
  updateProfile: (displayName: string) =>
    apiRequest<CurrentUser>('/api/account/profile', {
      method: 'PATCH',
      body: { displayName },
    }),
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.set('file', file)
    return apiRequest<CurrentUser>('/api/account/avatar', { method: 'POST', formData })
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/api/account/password', {
      method: 'PATCH',
      body: { currentPassword, newPassword },
    }),
}
