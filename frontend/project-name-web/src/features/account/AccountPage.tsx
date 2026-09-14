// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  ShellPageContainer,
  UserAvatar,
  ViewHeader,
} from '@vxture/design-system'

import { useAuthStore } from '@/stores/auth'
import { platformAvatarSrc } from '@/utils/avatar'

/**
 * 个人资料：只读展示平台身份。
 *
 * 显示名与头像归平台 IdP，本产品不存也不改——修改入口是控制台的个人资料页，
 * 地址由服务端在 /api/auth/me 里给出（consoleProfileUrl），这里不拼第二份。
 * 此前这一页调 /api/account/profile 与 /api/account/avatar：它们按本地账号 id 找人，
 * 对每一个平台用户都是 404；本地账号体系退役后这两个端点一并删除。
 */
export default function AccountPage() {
  const user = useAuthStore((state) => state.user)
  const displayName = user?.displayName || user?.username || '当前用户'
  const roleLabel = user?.admin ? '管理员' : '标书编制人员'
  const profileUrl = user?.consoleProfileUrl ?? null

  return (
    <section className="min-h-0 flex-1 overflow-y-auto" aria-label="个人资料">
      <ShellPageContainer width="base-xl">
        <ViewHeader
          icon="user-circle"
          title="个人资料"
          description="平台账号身份；资料在控制台维护"
        />

        <Card className="max-w-panel-md border border-border" surface="base">
          <CardHeader>
            <CardTitle>平台身份</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-lg">
            <div className="flex items-center gap-lg">
              <UserAvatar
                src={platformAvatarSrc(user?.avatarUrl)}
                alt={displayName}
                className="size-media-sm"
              />
              <div className="min-w-0">
                <div className="truncate text-title-sm text-foreground">{displayName}</div>
                <div className="mt-2xs truncate text-body-sm text-muted-foreground">
                  账号：{user?.username || '-'}
                </div>
                <div className="mt-2xs text-body-sm text-muted-foreground">角色：{roleLabel}</div>
              </div>
            </div>

            <p className="border-t border-border pt-lg text-body-sm text-muted-foreground">
              显示名称与头像来自平台账号，本产品只读。需要修改时请前往控制台。
            </p>
            {profileUrl ? (
              <a
                className="text-body-sm text-primary hover:underline"
                href={profileUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                在控制台修改资料
              </a>
            ) : null}
          </CardContent>
        </Card>
      </ShellPageContainer>
    </section>
  )
}
