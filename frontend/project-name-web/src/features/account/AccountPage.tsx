// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
import { useEffect, useRef, useState } from 'react'

import {
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Field,
  FieldLabel,
  Icon,
  Input,
  ShellPageContainer,
  Spinner,
  UserAvatar,
  ViewHeader,
} from '@vxture/design-system'

import { MutationError } from '@/components/QueryState'
import { useProtectedImageUrl } from '@/hooks/useProtectedImageUrl'
import { useAuthStore } from '@/stores/auth'

import {
  useUpdateProfileMutation,
  useUploadAvatarMutation,
} from './queries'

export default function AccountPage() {
  const avatarInput = useRef<HTMLInputElement>(null)
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const avatar = useProtectedImageUrl(user?.avatarUrl)
  const avatarMutation = useUploadAvatarMutation()
  const profileMutation = useUpdateProfileMutation()
  const [profileName, setProfileName] = useState(user?.displayName || '')
  const displayName = user?.displayName || user?.username || '当前用户'

  useEffect(() => {
    setProfileName(user?.displayName || '')
  }, [user?.displayName])

  const uploadAvatar = async (file: File) => {
    try {
      const updated = await avatarMutation.mutateAsync(file)
      setUser(updated)
    } catch {
      // Mutation error is shown next to the avatar action.
    } finally {
      if (avatarInput.current) avatarInput.current.value = ''
    }
  }

  const updateProfile = async () => {
    const normalized = profileName.trim()
    if (normalized.length < 2 || normalized.length > 64) return
    try {
      const updated = await profileMutation.mutateAsync(normalized)
      setUser(updated)
    } catch {
      // Mutation error is rendered below the profile form.
    }
  }

  return (
    <section className="min-h-0 flex-1 overflow-y-auto" aria-label="账号设置">
      <ShellPageContainer width="base-xl">
        <ViewHeader
          icon="user-circle"
          title="账号设置"
          description="维护个人资料与头像"
        />

        <div className="grid min-h-0 gap-lg lg:grid-cols-2">
          <Card className="border border-border" surface="base">
            <CardHeader>
              <CardTitle>个人资料</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-lg">
              <div className="flex items-center gap-lg">
                <UserAvatar
                  src={avatar.url || null}
                  alt={displayName}
                  className="size-media-sm"
                />
                <div className="min-w-0">
                  <div className="truncate text-title-sm text-foreground">{displayName}</div>
                  <div className="mt-2xs truncate text-body-sm text-muted-foreground">
                    账号：{user?.username || '-'}
                  </div>
                </div>
              </div>

              <Input
              ref={avatarInput}
              type="file"
              accept="image/png,image/jpeg,.png,.jpg,.jpeg"
              className="sr-only"
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) void uploadAvatar(file)
              }}
              />
              <div>
                <Button
                  variant="outline"
                  disabled={avatarMutation.isPending}
                  onClick={() => avatarInput.current?.click()}
                >
                  {avatarMutation.isPending ? (
                    <Spinner size="sm" />
                  ) : (
                    <Icon name="image" size="sm" />
                  )}
                  更换头像
                </Button>
                <MutationError error={avatarMutation.error} />
              </div>

              <form
                className="border-t border-border pt-lg"
                onSubmit={(event) => {
                  event.preventDefault()
                  void updateProfile()
                }}
              >
                <Field>
                  <FieldLabel htmlFor="profile-display-name">显示名称</FieldLabel>
                  <Input
                    id="profile-display-name"
                  value={profileName}
                  minLength={2}
                  maxLength={64}
                  onChange={(event) => setProfileName(event.target.value)}
                  />
                </Field>
                <Button
                  type="submit"
                  className="mt-sm"
                  size="sm"
                  variant="outline"
                  disabled={
                    profileMutation.isPending ||
                    profileName.trim() === (user?.displayName || '')
                  }
                >
                  {profileMutation.isPending ? (
                    <Spinner size="sm" />
                  ) : (
                    <Icon name="save" size="sm" />
                  )}
                  保存资料
                </Button>
                <MutationError error={profileMutation.error} />
              </form>
            </CardContent>
          </Card>
        </div>
      </ShellPageContainer>
    </section>
  )
}
