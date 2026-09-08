// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
import { ConfirmDestructive } from '@vxture/design-system'

import type { ManagedUser } from '@/types/admin'

type DeactivateUserDialogProps = {
  user: ManagedUser
  pending: boolean
  error: unknown
  onClose: () => void
  onConfirm: () => Promise<void>
}

export function DeactivateUserDialog({
  user,
  pending,
  error,
  onClose,
  onConfirm,
}: DeactivateUserDialogProps) {
  const errorDetail =
    error instanceof Error ? ` 上次操作失败：${error.message}` : ''

  return (
    <ConfirmDestructive
      open
      onOpenChange={(open) => {
        if (!open && !pending) onClose()
      }}
      verb="停用"
      target={user.displayName}
      consequence={`停用后该账号将立即退出，历史项目和操作记录仍会保留。${errorDetail}`}
      cancelLabel="取消"
      pendingLabel="正在停用"
      onConfirm={onConfirm}
    />
  )
}
