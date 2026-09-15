// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
/**
 * 平台身份的头像地址，可直接作为 `<img src>`。
 *
 * 头像来自 IdP 声明里的 `picture`，是平台侧的绝对 https 地址——**不走本站的受保护读取**。
 * 此前 Header 与账户页把它交给 `useProtectedImageUrl`，后者对 `${baseUrl}${path}` 做同源
 * 鉴权 fetch：对一个外部绝对地址这条路从来走不通，平台用户的头像一直是空的。
 *
 * 非 https 的值一律不渲染：历史本地账号的 `/api/account/avatar` 端点已退役，
 * 而 http 头像在 https 页面上也会被浏览器按混合内容拦下。
 */
export const platformAvatarSrc = (url: string | null | undefined): string | null => {
  const trimmed = url?.trim()
  return trimmed && /^https:\/\//i.test(trimmed) ? trimmed : null
}
