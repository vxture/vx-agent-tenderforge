// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-06
import {
  type IconName,
  StatusBadge,
  type StatusBadgeTone,
} from '@vxture/design-system'

import type { InterpretationObjectStatus } from '@/types/tender'

const presentation = {
  PENDING: {
    label: '等待生成',
    tone: 'neutral',
    icon: 'circle-dashed',
  },
  RUNNING: {
    label: '生成中',
    tone: 'info',
    icon: 'spinner',
  },
  SUCCEEDED: {
    label: '已完成',
    tone: 'success',
    icon: 'success',
  },
  FAILED: {
    label: '生成失败',
    tone: 'danger',
    icon: 'error',
  },
} satisfies Record<
  InterpretationObjectStatus,
  { label: string; tone: StatusBadgeTone; icon: IconName }
>

export function InterpretationObjectStatus({ status }: { status: InterpretationObjectStatus }) {
  const state = presentation[status]
  return (
    <StatusBadge
      tone={state.tone}
      icon={state.icon}
      className={status === 'RUNNING' ? '[&_svg]:animate-spin' : undefined}
    >
      {state.label}
    </StatusBadge>
  )
}
