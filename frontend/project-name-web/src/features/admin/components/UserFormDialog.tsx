// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { type FormEvent, useState } from 'react'

import {
  Button,
  Checkbox,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Field,
  FieldGroup,
  FieldLabel,
  Input,
  NativeSelect,
} from '@vxture/design-system'

import { MutationError } from '@/components/QueryState'
import type { ManagedUser } from '@/types/admin'

export type UserFormValue = {
  username: string
  displayName: string
  roleCode: ManagedUser['roleCode']
  enabled: boolean
  password: string
}

type UserFormDialogProps = {
  user: ManagedUser | null
  currentUserId: string
  submission: { pending: boolean; error: unknown }
  onClose: () => void
  onSubmit: (value: UserFormValue) => Promise<void>
}

export function UserFormDialog({
  user,
  currentUserId,
  submission,
  onClose,
  onSubmit,
}: UserFormDialogProps) {
  const editing = Boolean(user)
  const editingSelf = user?.id === currentUserId
  const [value, setValue] = useState<UserFormValue>({
    username: user?.username ?? '',
    displayName: user?.displayName ?? '',
    roleCode: user?.roleCode ?? 'PLANNER',
    enabled: user?.enabled ?? true,
    password: '',
  })

  /**
   * 提交用户表单。
   * @preconditions - 浏览器原生必填和长度校验已通过
   * @sideEffects - 调用父组件提供的创建或更新动作
   * @errorHandling - API 错误保留在弹窗中，避免丢失已填写内容
   */
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await onSubmit(value)
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !submission.pending) onClose()
      }}
    >
      <DialogContent width="md">
        <DialogHeader>
          <DialogTitle>{editing ? '编辑用户' : '新建用户'}</DialogTitle>
          <DialogDescription>
            {editing ? '更新账号资料、角色和登录状态。' : '创建新的 TenderAgent 登录账号。'}
          </DialogDescription>
        </DialogHeader>

        <form className="flex flex-col gap-lg" onSubmit={(event) => void submit(event)}>
          <FieldGroup>
            <Field>
              <FieldLabel htmlFor="user-username">用户名</FieldLabel>
              <Input
                id="user-username"
                required
                minLength={3}
                maxLength={64}
                pattern="[A-Za-z0-9._-]+"
                disabled={editing}
                value={value.username}
                onChange={(event) => setValue({ ...value, username: event.target.value })}
                placeholder="例如 zhangsan"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="user-display-name">姓名</FieldLabel>
              <Input
                id="user-display-name"
                required
                maxLength={64}
                value={value.displayName}
                onChange={(event) => setValue({ ...value, displayName: event.target.value })}
                placeholder="请输入用户姓名"
              />
            </Field>

            <Field>
              <FieldLabel htmlFor="user-role">角色</FieldLabel>
              <NativeSelect
                id="user-role"
                value={value.roleCode}
                disabled={editingSelf}
                onChange={(event) =>
                  setValue({ ...value, roleCode: event.target.value as ManagedUser['roleCode'] })
                }
              >
                <option value="PLANNER">投标编制员</option>
                <option value="ADMIN">系统管理员</option>
              </NativeSelect>
            </Field>

            <Field>
              <FieldLabel htmlFor="user-password">
                {editing ? '重置密码' : '初始密码'}
              </FieldLabel>
              <Input
                id="user-password"
                type="password"
                required={!editing}
                minLength={8}
                maxLength={72}
                value={value.password}
                onChange={(event) => setValue({ ...value, password: event.target.value })}
                placeholder={editing ? '不修改请留空' : '请输入 8 至 72 位密码'}
                autoComplete="new-password"
              />
            </Field>

            {editing ? (
              <label className="flex items-center gap-sm rounded-md border border-border px-md py-sm text-body-sm">
                <Checkbox
                  checked={value.enabled}
                  disabled={editingSelf}
                  onCheckedChange={(checked) =>
                    setValue({ ...value, enabled: checked === true })
                  }
                />
                <span className="text-label-md text-foreground">账号启用</span>
              </label>
            ) : null}

            <MutationError error={submission.error} />
          </FieldGroup>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submission.pending}>
              取消
            </Button>
            <Button type="submit" disabled={submission.pending}>
              {submission.pending ? '正在保存' : '保存'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
