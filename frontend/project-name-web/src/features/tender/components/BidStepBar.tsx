// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useNavigate } from 'react-router'

import { Button, cn,Icon } from '@vxture/design-system'

import type { BidStep } from '@/types/tender'

const steps: { key: BidStep; label: string; route: string }[] = [
  { key: 'SETUP', label: '标书设置', route: 'setup' },
  { key: 'INTERPRETATION', label: '招标文件解读', route: 'interpretation' },
  { key: 'OUTLINE', label: '目录编写', route: 'outline' },
  { key: 'CONTENT', label: '正文编写', route: 'content' },
]

export function BidStepBar({
  bidId,
  current,
  furthest,
}: {
  bidId?: string
  current: BidStep
  furthest: BidStep
}) {
  const navigate = useNavigate()
  const furthestIndex = steps.findIndex((step) => step.key === furthest)

  return (
    <ol
      className="grid h-full min-w-0 flex-1 grid-cols-4 bg-card"
      aria-label="标书编制步骤"
    >
      {steps.map((step, index) => {
        const active = step.key === current
        const completed = index < furthestIndex
        const accessible = Boolean(bidId) && index <= furthestIndex
        return (
          <li key={step.key} className="relative min-w-0">
            <Button
              variant="ghost"
              size="sm"
              disabled={!accessible}
              onClick={() => bidId && navigate(`/planner/bids/${bidId}/${step.route}`)}
              className={cn(
                'h-full w-full rounded-none border-b-2 px-2xs',
                active
                  ? 'border-primary text-primary-text'
                  : 'border-transparent text-muted-foreground',
                accessible && !active && 'hover:text-foreground'
              )}
            >
              <span
                className={cn(
                  'flex size-control-xs shrink-0 items-center justify-center rounded-full border text-label-sm',
                  active && 'border-primary bg-primary text-primary-foreground',
                  completed && !active && 'border-success bg-success-muted text-success-muted-foreground'
                )}
              >
                {completed ? <Icon name="check" size="xs" /> : index + 1}
              </span>
              <span className="hidden truncate lg:block">{step.label}</span>
            </Button>
          </li>
        )
      })}
    </ol>
  )
}
