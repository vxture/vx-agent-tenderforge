// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14

/**
 * 只接受站内路径作为登录后的回跳地址。
 *
 * `//host` 也以 `/` 开头，却是协议相对的站外地址——只判 `startsWith('/')` 会把人从登录流程带去
 * 任意域名。回到 `/login` 本身也不行，那会停在引导页。
 */
export function safeReturnTo(requested: string | null): string {
  if (!requested) return '/'
  if (!requested.startsWith('/') || requested.startsWith('//')) return '/'
  if (requested.startsWith('/login')) return '/'
  return requested
}

/**
 * 引导页「登录」的地址。
 *
 * 引导页就地渲染、不重定向，所以地址栏还是读者要去的那一页，回跳就带上它。旧链接 `/login?redirect=` 仍然认。
 */
export function loginHref(location: Pick<Location, 'pathname' | 'search'>): string {
  const target = location.pathname.startsWith('/login')
    ? safeReturnTo(new URLSearchParams(location.search).get('redirect'))
    : safeReturnTo(location.pathname + location.search)
  return `/api/auth/oidc/login?returnTo=${encodeURIComponent(target)}`
}
