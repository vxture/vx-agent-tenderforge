// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { create } from 'zustand'

import type { CurrentUser } from '@/types/auth'
import { storage } from '@/utils/storage'

type AuthState = {
  token: string
  user: CurrentUser | null
  setSession: (token: string, user: CurrentUser) => void
  setUser: (user: CurrentUser) => void
  clearAuth: () => void
  isAuthenticated: () => boolean
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

export const useAuthStore = create<AuthState>((set, get) => ({
  token: storage.get('token'),
  user: readUser(),
  setSession: (token, user) => {
    storage.set('token', token)
    storage.set('userInfo', JSON.stringify(user))
    set({ token, user })
  },
  setUser: (user) => {
    storage.set('userInfo', JSON.stringify(user))
    set({ user })
  },
  clearAuth: () => {
    storage.remove('token')
    storage.remove('userInfo')
    storage.remove('auth-storage')
    set({ token: '', user: null })
  },
  isAuthenticated: () => Boolean(get().token),
}))
