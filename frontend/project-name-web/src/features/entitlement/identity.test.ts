// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import type { CurrentUser } from '@/types/auth'

import { contactLineOf, formatPhone, identityLabelOf, workspaceLinesOf } from './identity'

const FALLBACK = '当前工作区'

const user = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 'usr_1',
  username: 'usr_1',
  displayName: '王小明',
  roleCode: 'PLANNER',
  avatarUrl: null,
  orgName: '华东设计院',
  workspaceName: '投标一部',
  email: 'wang@example.com',
  phone: '+8613800001234',
  admin: false,
  consoleProfileUrl: null,
  ...overrides,
})

describe('workspaceLinesOf', () => {
  it('组织一行、工作区一行，各自完整', () => {
    expect(workspaceLinesOf(user(), FALLBACK)).toEqual({ orgName: '华东设计院', workspaceName: '投标一部' })
  })

  it('组织与工作区同名时只写一行', () => {
    expect(workspaceLinesOf(user({ orgName: '投标一部' }), FALLBACK)).toEqual({ orgName: null, workspaceName: '投标一部' })
  })

  it('没有组织名时只写工作区', () => {
    expect(workspaceLinesOf(user({ orgName: null }), FALLBACK)).toEqual({ orgName: null, workspaceName: '投标一部' })
  })

  it('没有工作区名时第二行用兜底文案，不拿组织名顶替', () => {
    expect(workspaceLinesOf(user({ workspaceName: null }), FALLBACK)).toEqual({
      orgName: '华东设计院',
      workspaceName: FALLBACK,
    })
  })

  it('空白名字等同没给', () => {
    expect(workspaceLinesOf(user({ orgName: ' ', workspaceName: '  ' }), FALLBACK)).toEqual({
      orgName: null,
      workspaceName: FALLBACK,
    })
  })

  it('还没有用户时只有兜底文案', () => {
    expect(workspaceLinesOf(null, FALLBACK)).toEqual({ orgName: null, workspaceName: FALLBACK })
  })
})

describe('identityLabelOf', () => {
  it('显示平台签发的名字', () => {
    expect(identityLabelOf(user())).toBe('王小明')
  })

  it('平台没给名字时退到账号标识', () => {
    expect(identityLabelOf(user({ displayName: '' }))).toBe('usr_1')
  })

  it('还没有用户时为空', () => {
    expect(identityLabelOf(null)).toBe('')
  })
})

describe('contactLineOf', () => {
  it('人名下面显示手机号', () => {
    expect(contactLineOf(user())).toBe('138 0000 1234')
  })

  it('账号没有手机号时用邮箱，两边仍是两行', () => {
    expect(contactLineOf(user({ phone: null }))).toBe('wang@example.com')
  })

  it('空白手机号等同没给', () => {
    expect(contactLineOf(user({ phone: '  ' }))).toBe('wang@example.com')
  })

  it('手机号和邮箱都没有时只显示名字', () => {
    expect(contactLineOf(user({ phone: null, email: null }))).toBeNull()
    expect(contactLineOf(null)).toBeNull()
  })
})

describe('formatPhone', () => {
  it('大陆手机号按 3-4-4 分组，去掉国家码', () => {
    expect(formatPhone('+8613800001234')).toBe('138 0000 1234')
    expect(formatPhone('8613800001234')).toBe('138 0000 1234')
    expect(formatPhone('13800001234')).toBe('138 0000 1234')
    expect(formatPhone('138-0000-1234')).toBe('138 0000 1234')
  })

  it('其它号码原样显示，不猜格式', () => {
    expect(formatPhone('+85291234567')).toBe('+85291234567')
    expect(formatPhone('021-12345678')).toBe('021-12345678')
  })
})
