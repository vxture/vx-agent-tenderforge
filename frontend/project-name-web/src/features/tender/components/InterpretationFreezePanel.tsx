// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-05
import {
  Button,
  Card,
  CardContent,
  Icon,
  Spinner,
  StatusBadge,
} from '@vxture/design-system'

import type { BidProductionState } from '@/types/tender'

export function InterpretationFreezePanel({
  production,
  freezing,
  onFreeze,
}: {
  production: BidProductionState
  freezing: boolean
  onFreeze: () => Promise<void>
}) {
  const frozen = production.interpretationStatus === 'FROZEN'

  return (
    <Card className="border border-border">
      <CardContent className="flex flex-wrap items-center justify-between gap-lg">
        <div>
          <h2 className="text-title-sm">解读确认</h2>
          <p className="mt-2xs text-body-sm text-muted-foreground">
            {frozen
              ? `当前解读已冻结为版本 ${production.interpretationVersion}`
              : '确认后，目录生成将使用上方两项解读结果'}
          </p>
        </div>
        <div className="flex items-center gap-sm">
          <StatusBadge
            tone={frozen ? 'success' : 'warning'}
            icon={frozen ? 'check' : 'lock'}
          >
            {frozen ? '已冻结' : '待冻结'}
          </StatusBadge>
          <Button type="button" disabled={frozen || freezing} onClick={() => void onFreeze()}>
            {freezing ? <Spinner size="sm" /> : <Icon name="lock" size="sm" />}
            {freezing ? '保存并冻结中' : frozen ? '解读已冻结' : '确认解读并冻结'}
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
