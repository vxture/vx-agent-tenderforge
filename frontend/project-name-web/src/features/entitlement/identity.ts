// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { CurrentUser } from '@/types/auth'

// 门禁页身份块的取值（身份块本身见 app/components/gate-identity.tsx）。
//
// 名字与联系方式全部来自平台 access token（name、phone、email、active_org_name、active_workspace_name），
// 经会话与 `/api/auth/me` 原样带到这里。此前门禁页显示的是 `usr_<uuid>` 与兜底文案「当前工作区」：
// 平台把名字签在 access token 里，服务端却只从 id_token 读。

/** 登录身份：显示名；平台没给名字时退到账号标识，总比一栏空白好认。 */
export function identityLabelOf(user: CurrentUser | null | undefined): string {
  return user?.displayName || user?.username || ''
}

/**
 * 人名下面那一行（owner 2026-09-16：人员、单位左右布局，人名下显示电话号码，与单位一侧两行对齐）。
 *
 * 先手机号；账号没有手机号（邮箱注册）时用邮箱，两边仍是两行；都没有时为 null，只显示名字。
 */
export function contactLineOf(user: CurrentUser | null | undefined): string | null {
  const phone = user?.phone?.trim()
  if (phone) return formatPhone(phone)
  return user?.email?.trim() || null
}

// 中国大陆手机号：可带 +86 / 86 前缀。
const CN_MOBILE = /^(?:\+?86)?(1\d{10})$/

/** 大陆手机号按 3-4-4 分组、去掉国家码（读的人是本人，不需要 +86）；其它号码原样显示，不猜格式。 */
export function formatPhone(raw: string): string {
  const match = raw.replace(/[\s-]/g, '').match(CN_MOBILE)
  if (!match) return raw
  const digits = match[1]
  return `${digits.slice(0, 3)} ${digits.slice(3, 7)} ${digits.slice(7)}`
}

export interface WorkspaceLines {
  /** 第一行。平台没给、或与工作区同名时为 null——同一个名字不写两行。 */
  readonly orgName: string | null
  /** 第二行。平台没给时是兜底文案，不拿组织名或标识凑数。 */
  readonly workspaceName: string
}

/**
 * 组织与工作区，分两行（owner 2026-09-16）。
 *
 * 此前拼成「组织 / 工作区」一串，放进卡片会被截断，截掉的正是工作区名。两行各自完整。
 * 组织要说出来：同名工作区（「default workspace」）在不同组织里很常见，只写工作区名，走错组织的人发现不了。
 */
export function workspaceLinesOf(user: CurrentUser | null | undefined, fallback: string): WorkspaceLines {
  const workspace = user?.workspaceName?.trim() || null
  const org = user?.orgName?.trim() || null
  return {
    orgName: org && org !== workspace ? org : null,
    workspaceName: workspace ?? fallback,
  }
}
