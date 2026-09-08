// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
export interface CurrentUser {
  id: string
  username: string
  displayName: string
  roleCode: 'ADMIN' | 'PLANNER'
  avatarUrl: string | null
  admin: boolean
}

export interface LoginResult {
  token: string
  user: CurrentUser
  expiresInSeconds: number
}
