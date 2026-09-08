// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-27
import {
  StatusBadge as DesignStatusBadge,
  type StatusBadgeTone,
} from '@vxture/design-system'

const statusTone: Record<string, StatusBadgeTone> = {
  成功: 'success',
  已启用: 'success',
  失败: 'danger',
  拒绝: 'danger',
  已停用: 'neutral',
}

type StatusBadgeProps = {
  label: string
  status: string
}

export function StatusBadge({ label, status }: StatusBadgeProps) {
  return (
    <DesignStatusBadge tone={statusTone[status] ?? 'neutral'}>
      {label}
    </DesignStatusBadge>
  )
}
