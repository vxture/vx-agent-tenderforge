// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
/**
 * `/api/auth/me` 的响应：平台身份，外加控制台资料页地址。
 *
 * 显示名与头像归平台 IdP，本产品只读，不提供任何修改接口。
 */
export interface CurrentUser {
  id: string
  username: string
  displayName: string
  roleCode: 'ADMIN' | 'PLANNER'
  /** IdP 声明里的 picture：平台侧的绝对地址，经 platformAvatarSrc 直接渲染。 */
  avatarUrl: string | null
  /**
   * 平台 access token 的 active_org_name / active_workspace_name，只用于渲染。
   * 登录时写进会话；平台没签发、或会话建立于这两个字段上线之前，为 null。
   */
  orgName: string | null
  workspaceName: string | null
  admin: boolean
  /**
   * 控制台个人资料页。由服务端按 CONSOLE_BASE_URL 单点拼出——前端不再存第二份地址。
   * 未配置时为 null，界面不渲染入口。
   */
  consoleProfileUrl: string | null
}
