// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { isRejection } from '@/api/client'

/**
 * 一次命令被权益拒绝时，界面要说什么、给用户什么出路。
 *
 * 只认两个与订阅有关的拒绝码：`NOT_ENTITLED`（没买 / 失效 / 档位不含）与
 * `QUOTA_EXCEEDED`（额度用尽）。另外三个同义码——`POLICY_DENIED`、`APPROVAL_REQUIRED`、
 * `RATE_LIMITED`——出路不是去 console 订阅，把它们也渲染成「前往订阅」会把人带到一个
 * 解决不了问题的页面，所以这里返回 null，交给普通错误展示。
 *
 * 标题用服务端给的 message：它说清了是哪一种「不能」（见 EntitlementGuard）。
 * 这里不做商业推断——该买哪档、多少钱，归 console。
 */
export type RejectionNotice = {
  title: string
  description: string
  actionLabel: string
}

export function rejectionNotice(error: unknown): RejectionNotice | null {
  if (!isRejection(error)) return null
  switch (error.code) {
    case 'NOT_ENTITLED':
      return {
        title: error.message,
        description: '订阅与升级在控制台完成，完成后回到这里刷新即可继续。已有的标书仍可查看。',
        actionLabel: '前往订阅',
      }
    case 'QUOTA_EXCEEDED':
      return {
        title: error.message,
        description: '当前额度已用尽，可在控制台查看用量或加购额度。',
        actionLabel: '查看用量与额度',
      }
    default:
      return null
  }
}
