import { QueryClient } from '@tanstack/react-query'

// 创建 QueryClient 实例
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 默认配置
      staleTime: 1000 * 60 * 5, // 5分钟内数据视为新鲜
      retry: 1, // 失败重试1次
      refetchOnWindowFocus: false, // 窗口聚焦时不自动刷新
    },
  },
})
