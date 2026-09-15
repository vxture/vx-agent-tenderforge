// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import type { ReactNode } from 'react'

import { Button } from '@vxture/design-ui'

// 拒绝页共用的动作块（参照 vx-agent-yucer app/(app)/components/gate-actions.tsx，照搬）。
//
// 它存在是因为走过样：各页各长出自己的出路，同一个退出一个带图标一个不带、两种高度。
// 同一个动作三份实现就有三次走样的机会，这里只有一份。
//
// 形状：占满宽度，主按钮在上、次按钮在下，中间什么都没有。一张拒绝页只有一件事可做、一条出路。

export function GateActions({
  primary,
  secondary,
}: {
  readonly primary: ReactNode
  readonly secondary?: ReactNode
}) {
  return (
    <div className="gap-sm flex w-full flex-col">
      {primary}
      {secondary}
    </div>
  )
}

/**
 * 这一页的主动作。每页一个。
 *
 * `external`：去控制台订阅这类站外地址，只在点击时新开窗口，本页不跳走（通则 C2）。
 */
export function GatePrimary({
  href,
  external = false,
  children,
}: {
  readonly href: string
  readonly external?: boolean
  readonly children: ReactNode
}) {
  return (
    <Button asChild size="lg" className="w-full">
      <a href={href} {...(external ? { target: '_blank', rel: 'noopener noreferrer' } : {})}>
        {children}
      </a>
    </Button>
  )
}

/**
 * 不是去某个地址、而是就地再问一次的主动作（订阅状态暂时拿不到时的「重试」）。
 * 与 GatePrimary 同尺寸同分量：它就是这一页的主动作。本产品比 yucer 多出这一种。
 */
export function GatePrimaryButton({
  onClick,
  disabled = false,
  children,
}: {
  readonly onClick: () => void
  readonly disabled?: boolean
  readonly children: ReactNode
}) {
  return (
    <Button type="button" size="lg" className="w-full" disabled={disabled} onClick={onClick}>
      {children}
    </Button>
  )
}

/** 出路，或另一扇门。每页同尺寸同分量。 */
export function GateSecondary({ href, children }: { readonly href: string; readonly children: ReactNode }) {
  return (
    <Button asChild variant="outline" size="lg" className="w-full">
      <a href={href}>{children}</a>
    </Button>
  )
}

/**
 * 退出登录：一次 POST，不是链接。
 *
 * 真实的表单，不是脚本点击：这几页各自只有一个出路，出路不能依赖脚本是否已经跑起来。
 * 服务端对表单提交直接 302 去平台登出端点，并种下已退出便条。与 GateSecondary 同尺寸同分量。
 */
export function GateSignOut({ children }: { readonly children: ReactNode }) {
  return (
    <form method="post" action="/api/auth/logout" className="w-full">
      <Button type="submit" variant="outline" size="lg" className="w-full">
        {children}
      </Button>
    </form>
  )
}
