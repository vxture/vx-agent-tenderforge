// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { platformAvatarSrc } from './avatar'

/**
 * 平台头像直接渲染 IdP 给的绝对地址，其余一律不渲染。
 *
 * 此前头像被当成本站受保护路径去同源鉴权 fetch，对外部地址从来走不通——
 * 平台用户的头像一直是空的，而界面上只是显示了一个首字母占位，看不出坏了。
 */
describe('platformAvatarSrc', () => {
  it('原样返回 IdP 的 https 头像地址', () => {
    expect(platformAvatarSrc('https://idp.example.test/a.png')).toBe('https://idp.example.test/a.png')
    expect(platformAvatarSrc('  https://idp.example.test/a.png  ')).toBe('https://idp.example.test/a.png')
  })

  it('不渲染已退役的本地头像路径、明文 http 与空值', () => {
    expect(platformAvatarSrc('/api/account/avatar?v=1')).toBeNull()
    expect(platformAvatarSrc('http://idp.example.test/a.png')).toBeNull()
    expect(platformAvatarSrc('javascript:alert(1)')).toBeNull()
    expect(platformAvatarSrc('   ')).toBeNull()
    expect(platformAvatarSrc(null)).toBeNull()
    expect(platformAvatarSrc(undefined)).toBeNull()
  })
})
