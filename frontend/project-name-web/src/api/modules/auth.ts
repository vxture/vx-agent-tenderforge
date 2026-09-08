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
  me: () => apiRequest<CurrentUser>('/api/auth/me'),
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
