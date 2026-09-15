import { RouterProvider } from 'react-router'

import { FullscreenProvider, ThemeProvider, ToastProvider } from '@vxture/design-system'

import { MessagesProvider } from '@/app/lib/i18n/provider'
import { QueryProvider } from '@/config/QueryProvider'
import router from '@/router/index'

/**
 * App 根组件
 * 用于配置全局 Provider、主题、国际化等
 *
 * 词典 Provider 包住整棵树：门禁页在路由守卫里就地渲染，任何一层都可能用到它。
 */
function App() {
  return (
    <ThemeProvider defaultMode="light" defaultDensity="default">
      <FullscreenProvider>
        <ToastProvider>
          <MessagesProvider>
            <QueryProvider>
              <RouterProvider router={router} />
            </QueryProvider>
          </MessagesProvider>
        </ToastProvider>
      </FullscreenProvider>
    </ThemeProvider>
  )
}

export default App
