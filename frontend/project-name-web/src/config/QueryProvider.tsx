import type { ReactNode } from 'react'

import { QueryClientProvider } from '@tanstack/react-query'

import { queryClient } from './queryClient'

interface QueryProviderProps {
  children: ReactNode
}

/**
 * TanStack Query Provider
 * 在 App 根组件中使用
 */
export function QueryProvider({ children }: QueryProviderProps) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
