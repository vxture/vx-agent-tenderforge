// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { create } from 'zustand'

import type { CurrentUser } from '@/types/auth'
import { storage } from '@/utils/storage'

/**
 * 当前用户的界面缓存。
 *
 * **这里没有 token，也不该有。** 会话是服务端的 HttpOnly cookie，浏览器读不到；
 * 是否已登录由服务端裁定（见 AuthGuard）。本地口令通道 2026-09-15 退役后，
 * 前端不持有任何凭据——localStorage 里只剩一份用于首屏渲染的用户资料。
 */
type AuthState = {
  user: CurrentUser | null
  setUser: (user: CurrentUser) => void
  clearAuth: () => void
}

const readUser = (): CurrentUser | null => {
  const value = storage.get('userInfo')
  if (!value) return null
  try {
    return JSON.parse(value) as CurrentUser
  } catch {
    return null
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  user: readUser(),
  setUser: (user) => {
    storage.set('userInfo', JSON.stringify(user))
    set({ user })
  },
  clearAuth: () => {
    // 'token' 是退役的口令通道留在浏览器里的旧值，它已不能认证任何请求，顺手清掉。
    storage.remove('token')
    storage.remove('userInfo')
    storage.remove('auth-storage')
    set({ user: null })
  },
}))
