import { RouterProvider } from 'react-router'

import { FullscreenProvider, ThemeProvider, ToastProvider } from '@vxture/design-system'

import { QueryProvider } from '@/config/QueryProvider'
import router from '@/router/index'

/**
 * App 根组件
 * 用于配置全局 Provider、主题、国际化等
 */
function App() {
  return (
    <ThemeProvider defaultMode="light" defaultDensity="default">
      <FullscreenProvider>
        <ToastProvider>
          <QueryProvider>
            <RouterProvider router={router} />
          </QueryProvider>
        </ToastProvider>
      </FullscreenProvider>
    </ThemeProvider>
  )
}

export default App
