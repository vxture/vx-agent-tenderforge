// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-31
import { useEffect, useRef, useState } from 'react'

import {
  Banner,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Field,
  FieldGroup,
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
  useChangePasswordMutation,
  useUpdateProfileMutation,
  useUploadAvatarMutation,
} from './queries'

export default function AccountPage() {
  const avatarInput = useRef<HTMLInputElement>(null)
  const user = useAuthStore((state) => state.user)
  const setUser = useAuthStore((state) => state.setUser)
  const clearAuth = useAuthStore((state) => state.clearAuth)
  const avatar = useProtectedImageUrl(user?.avatarUrl)
  const avatarMutation = useUploadAvatarMutation()
  const profileMutation = useUpdateProfileMutation()
  const passwordMutation = useChangePasswordMutation()
  const [profileName, setProfileName] = useState(user?.displayName || '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPasswords, setShowPasswords] = useState(false)
  const [validationError, setValidationError] = useState('')
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

  /**
   * 修改当前用户密码，成功后清理本地会话并要求重新登录。
   * @preconditions - 当前密码正确，新密码长度为8至64个字符且两次输入一致
   * @sideEffects - 调用密码修改接口并撤销当前用户全部会话
   * @errorHandling - 表单校验和接口错误均保留在当前页面显示
   */
  const changePassword = async () => {
    setValidationError('')
    if (newPassword !== confirmPassword) {
      setValidationError('两次输入的新密码不一致')
      return
    }
    if (newPassword.length < 8 || newPassword.length > 64) {
      setValidationError('新密码长度必须为8至64个字符')
      return
    }
    try {
      await passwordMutation.mutateAsync({ currentPassword, newPassword })
      clearAuth()
      window.location.href = '/login?passwordChanged=1'
    } catch {
      // Mutation error is rendered below the form.
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
          description="维护个人资料、头像与登录密码"
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

          <Card className="border border-border" surface="base">
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>修改密码</CardTitle>
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                className="text-muted-foreground hover:text-foreground"
                onClick={() => setShowPasswords((current) => !current)}
                aria-label={showPasswords ? '隐藏密码' : '显示密码'}
                title={showPasswords ? '隐藏密码' : '显示密码'}
              >
                <Icon name={showPasswords ? 'eye-slash' : 'eye'} size="sm" />
              </Button>
            </CardHeader>

            <form
              className="flex max-w-panel-md flex-col gap-lg px-xl"
              onSubmit={(event) => {
                event.preventDefault()
                void changePassword()
              }}
            >
              <FieldGroup>
                <PasswordField
                  label="当前密码"
                  value={currentPassword}
                  visible={showPasswords}
                  autoComplete="current-password"
                  onChange={setCurrentPassword}
                />
                <PasswordField
                  label="新密码"
                  value={newPassword}
                  visible={showPasswords}
                  autoComplete="new-password"
                  onChange={setNewPassword}
                />
                <PasswordField
                  label="确认新密码"
                  value={confirmPassword}
                  visible={showPasswords}
                  autoComplete="new-password"
                  onChange={setConfirmPassword}
                />
              </FieldGroup>
              {validationError ? (
                <Banner tone="danger" title={validationError} />
              ) : null}
              <MutationError error={passwordMutation.error} />
              <div>
                <Button
                  type="submit"
                  disabled={
                    passwordMutation.isPending ||
                    !currentPassword ||
                    !newPassword ||
                    !confirmPassword
                  }
                >
                  {passwordMutation.isPending ? (
                    <Spinner size="sm" />
                  ) : null}
                  修改密码
                </Button>
              </div>
            </form>
          </Card>
        </div>
      </ShellPageContainer>
    </section>
  )
}

type PasswordFieldProps = {
  label: string
  value: string
  visible: boolean
  autoComplete: string
  onChange: (value: string) => void
}

function PasswordField({ label, value, visible, autoComplete, onChange }: PasswordFieldProps) {
  return (
    <Field>
      <FieldLabel>{label}</FieldLabel>
      <Input
        type={visible ? 'text' : 'password'}
        value={value}
        autoComplete={autoComplete}
        maxLength={128}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  )
}
