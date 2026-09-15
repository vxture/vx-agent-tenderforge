import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import App from '@/App'
import { resolveLocale } from '@/app/lib/i18n/locale'

import '@vxture/design-system/styles/globals.css'
import '@vxture/design-system/styles/brands/vxture.css'
import '@/styles/globals.css'

// <html lang> 取这次访问解析出的语言，而不是 index.html 里的占位：否则英文页面对读屏软件与浏览器翻译都自称中文。
// 首次渲染之前写，词典 Provider 之后随切换保持同步。
document.documentElement.lang = resolveLocale()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
