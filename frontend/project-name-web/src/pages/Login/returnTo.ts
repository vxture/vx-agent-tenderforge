// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14

/**
 * 只接受站内路径作为登录后的回跳地址。
 *
 * `//host` 也以 `/` 开头，却是协议相对的站外地址——原先只判 `startsWith('/')`，
 * 会把人从登录流程带去任意域名。回到 `/login` 本身也不行，那会停在这一页。
 *
 * 单独成文件，是因为登录页组件文件只能导出组件（react-refresh 的约束）。
 */
export function safeReturnTo(requested: string | null): string {
  if (!requested) return '/'
  if (!requested.startsWith('/') || requested.startsWith('//')) return '/'
  if (requested.startsWith('/login')) return '/'
  return requested
}
