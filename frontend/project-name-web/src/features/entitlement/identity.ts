// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { CurrentUser } from '@/types/auth'

// 门禁页上「谁在登录、被拒的是哪个工作区」两栏的取值。
//
// 名字全部来自平台 access token（name、active_org_name、active_workspace_name），经会话与
// `/api/auth/me` 原样带到这里。此前这两栏显示的是 `usr_<uuid>` 与兜底文案「当前工作区」：
// 平台把名字签在 access token 里，服务端却只从 id_token 读。

/** 登录身份：显示名；平台没给名字时退到账号标识，总比一栏空白好认。 */
export function identityLabelOf(user: CurrentUser | null | undefined): string {
  return user?.displayName || user?.username || ''
}

/**
 * 当前工作区：「组织 / 工作区」。
 *
 * 组织要说出来：同名工作区在不同组织里很常见，只写工作区名，走错组织的人发现不了自己走错了。
 * 两者同名时只写一次；平台没给工作区名时用兜底文案，不拿组织名或标识凑数。
 */
export function workspaceLabelOf(user: CurrentUser | null | undefined, fallback: string): string {
  const workspace = user?.workspaceName?.trim()
  if (!workspace) return fallback
  const org = user?.orgName?.trim()
  return org && org !== workspace ? `${org} / ${workspace}` : workspace
}
