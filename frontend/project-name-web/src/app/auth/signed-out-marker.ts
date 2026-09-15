// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

// 把产品根路径变成「已退出登录」确认页的便条（参照 vx-agent-yucer app/auth/lib/signed-out-marker.ts）。
//
// IdP 的退出回跳地址是平台登记的产品根路径，也就是未登录引导页：主动退出会被回以「登录」，
// 看起来像退出失败。登记地址产品改不了，所以 `POST /api/auth/logout` 在去 IdP 的路上种下这张便条
// （服务端 SignedOutMarker，120 秒、非 HttpOnly），没有会话时读到它就渲染确认页。
//
// 确认页挂载时由客户端清掉，只显示一次。名字必须与服务端 SignedOutMarker.NAME 一致。

export const SIGNED_OUT_COOKIE = 'tenderforge_signed_out'

export function hasSignedOutMarker(): boolean {
  return document.cookie.split('; ').some((entry) => entry === `${SIGNED_OUT_COOKIE}=1`)
}

export function clearSignedOutMarker(): void {
  document.cookie = `${SIGNED_OUT_COOKIE}=; path=/; max-age=0; samesite=lax`
}
