// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
import { Card, Icon, UserAvatar } from '@vxture/design-ui'

import { useMessages } from '../lib/i18n/provider'

// 门禁页上「谁在登录、在哪个工作区」的身份块（owner 2026-09-16）。
//
// 两项，都不带标签——内容自己说明是什么：
// - 人：头像 + 名字。头像是平台签发的 picture；没有时 UserAvatar 自己画默认剪影，不渲染空 <img>。
// - 工作区：组织图标 + 两行，第一行组织名，第二行工作区名。
//
// 为什么不照搬 yucer 的两列 LabeledValue：真实的「组织 / 工作区」塞进半张卡片会被截断，截掉的正好是
// 工作区名——读者最需要的那一半（yucer 只显示兜底短文案，所以没暴露）。这里名字换行，不截断。
//
// 组织图标是设计系统的 building，不是组织自己的 logo：平台的组织 logo 目前只由控制台在它自己的会话里提供，
// token 里没有，本产品取不到。平台给出 RP 可取的地址之后，换成组织 logo 只改这一处。

export interface GateIdentityProps {
  readonly userName: string
  /** 平台 picture（https）；null 时显示默认剪影。 */
  readonly avatarSrc: string | null
  /** 平台没给、或与工作区同名时为 null，只显示工作区一行。 */
  readonly orgName: string | null
  readonly workspaceName: string
}

export function GateIdentity({ userName, avatarSrc, orgName, workspaceName }: GateIdentityProps) {
  const { SHELL_TEXT } = useMessages()

  return (
    <Card
      surface="soft"
      role="group"
      aria-label={SHELL_TEXT.identityCardLabel}
      className="gap-md p-lg grid w-full text-left"
    >
      <div className="gap-sm flex items-center">
        <UserAvatar src={avatarSrc} alt={userName} className="size-10 shrink-0" />
        <span className="text-body-md min-w-0 font-medium break-words">{userName}</span>
      </div>

      <div className="gap-sm flex items-center">
        <span
          aria-hidden
          className="bg-muted text-muted-foreground flex size-10 shrink-0 items-center justify-center rounded-full"
        >
          <Icon name="building" size="sm" />
        </span>
        <div className="min-w-0">
          {orgName && <p className="text-body-md font-medium break-words">{orgName}</p>}
          <p className={orgName ? 'text-body-md text-muted-foreground break-words' : 'text-body-md font-medium break-words'}>
            {workspaceName}
          </p>
        </div>
      </div>
    </Card>
  )
}
