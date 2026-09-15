// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'

import { FullscreenProvider, ThemeProvider } from '@vxture/design-system'

import { MessagesProvider } from '@/app/lib/i18n/provider'

/**
 * 门禁页在测试里需要的根 Provider：主题（页头主题切换）、全屏（全屏切换）、词典（固定 zh-CN，
 * 不让测试结果随运行机器的浏览器语言变）。与 App.tsx 同一套顺序。
 */
export function GateProviders({ children }: { children: ReactNode }) {
  return (
    <ThemeProvider defaultMode="light" defaultDensity="default">
      <FullscreenProvider>
        <MessagesProvider initialLocale="zh-CN">{children}</MessagesProvider>
      </FullscreenProvider>
    </ThemeProvider>
  )
}
