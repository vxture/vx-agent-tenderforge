// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { ApiError } from '@/api/client'

import { rejectionNotice } from './rejection'

/**
 * 被权益拒绝的命令，界面给出订阅引导；其余失败不冒充成订阅问题。
 *
 * 两个方向都要守：把 NOT_ENTITLED 渲染成一句「请求失败」，用户不知道出路；
 * 把 RATE_LIMITED 渲染成「前往订阅」，用户被带到一个解决不了问题的页面。
 */
describe('rejectionNotice', () => {
  it('未订阅 / 失效 / 档位不含：给出订阅引导，标题照读服务端说明', () => {
    const notice = rejectionNotice(
      new ApiError('当前工作空间尚未订阅标书编写智能体，无法使用 AI 解读与生成', 403, 'NOT_ENTITLED', false)
    )

    expect(notice).not.toBeNull()
    expect(notice?.title).toContain('尚未订阅')
    expect(notice?.actionLabel).toBe('前往订阅')
  })

  it('额度用尽：引导看用量与额度，而不是首购', () => {
    const notice = rejectionNotice(new ApiError('额度已用尽', 403, 'QUOTA_EXCEEDED', false))

    expect(notice?.actionLabel).toBe('查看用量与额度')
  })

  it.each([
    ['RATE_LIMITED', 429, true],
    ['POLICY_DENIED', 403, false],
    ['APPROVAL_REQUIRED', 403, false],
  ])('%s 的出路不是订阅，不渲染订阅引导', (code, status, retryable) => {
    expect(rejectionNotice(new ApiError('x', status, code, retryable))).toBeNull()
  })

  it('普通业务失败与非 ApiError 不冒充成订阅问题', () => {
    expect(rejectionNotice(new ApiError('请先冻结正文', 409, 'BID_CONTENT_NOT_FROZEN', false))).toBeNull()
    expect(rejectionNotice(new Error('boom'))).toBeNull()
    expect(rejectionNotice(null)).toBeNull()
  })
})
