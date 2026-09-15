// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { Button } from '@vxture/design-system'

type CursorPagerFooterProps = {
  /** 本页条数。没有总数：列表会增长，总数一返回就过时（通则 A-3）。 */
  count: number
  unit: string
  canGoPrevious: boolean
  nextCursor: string | null
  onPrevious: () => void
  onNext: (cursor: string) => void
}

/** 游标列表的页脚：本页条数 + 上一页 / 下一页。 */
export function CursorPagerFooter({
  count,
  unit,
  canGoPrevious,
  nextCursor,
  onPrevious,
  onNext,
}: CursorPagerFooterProps) {
  return (
    <div className="flex items-center justify-between gap-md">
      <span className="text-sm text-muted-foreground">
        本页 {count} {unit}
      </span>
      <div className="flex items-center gap-sm">
        <Button variant="outline" size="sm" disabled={!canGoPrevious} onClick={onPrevious}>
          上一页
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={nextCursor === null}
          onClick={() => nextCursor && onNext(nextCursor)}
        >
          下一页
        </Button>
      </div>
    </div>
  )
}
