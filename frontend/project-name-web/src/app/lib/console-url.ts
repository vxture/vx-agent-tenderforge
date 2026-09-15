// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

// 账号中心（平台控制台），已退出确认页的次按钮去这里。
//
// 已退出的人没有会话，拿不到 `/api/auth/me` 里服务端拼好的地址，所以这一处由构建配置给出。
// 与后端 CONSOLE_BASE_URL 同一个默认值；显式置空表示不渲染按钮——一个哪里都不去的按钮比没有按钮更糟。

export function consoleUrl(): string | null {
  const raw = import.meta.env.VITE_CONSOLE_URL as string | undefined
  if (raw === '') return null
  return (raw ?? 'https://console.vxture.com').replace(/\/$/, '')
}
