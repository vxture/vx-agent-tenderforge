// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import type { CurrentUser } from '@/types/auth'

import { identityLabelOf, workspaceLabelOf } from './identity'

const FALLBACK = '当前工作区'

const user = (overrides: Partial<CurrentUser> = {}): CurrentUser => ({
  id: 'usr_1',
  username: 'usr_1',
  displayName: '王小明',
  roleCode: 'PLANNER',
  avatarUrl: null,
  orgName: '华东设计院',
  workspaceName: '投标一部',
  admin: false,
  consoleProfileUrl: null,
  ...overrides,
})

describe('workspaceLabelOf', () => {
  it('组织与工作区都说出来', () => {
    expect(workspaceLabelOf(user(), FALLBACK)).toBe('华东设计院 / 投标一部')
  })

  it('组织与工作区同名时只写一次', () => {
    expect(workspaceLabelOf(user({ orgName: '投标一部' }), FALLBACK)).toBe('投标一部')
  })

  it('没有组织名时只写工作区', () => {
    expect(workspaceLabelOf(user({ orgName: null }), FALLBACK)).toBe('投标一部')
  })

  it('没有工作区名时用兜底文案，不拿组织名顶替', () => {
    expect(workspaceLabelOf(user({ workspaceName: null }), FALLBACK)).toBe(FALLBACK)
  })

  it('空白名字等同没给', () => {
    expect(workspaceLabelOf(user({ workspaceName: '  ' }), FALLBACK)).toBe(FALLBACK)
  })

  it('还没有用户时用兜底文案', () => {
    expect(workspaceLabelOf(null, FALLBACK)).toBe(FALLBACK)
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
