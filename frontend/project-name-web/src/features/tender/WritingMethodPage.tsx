// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useNavigate } from 'react-router'

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  EntryCard,
  ShellPageContainer,
  StatusBadge,
  ViewHeader,
} from '@vxture/design-system'

export default function WritingMethodPage() {
  const navigate = useNavigate()

  return (
    <section className="h-full overflow-y-auto">
      <ShellPageContainer width="narrow-lg">
        <ViewHeader
          icon="edit"
          title="标书写作"
          description="选择本次投标文件的编写方式"
        />

        <div className="grid gap-lg md:grid-cols-2">
          <EntryCard
            icon="list-checks"
            title="按招标评分点写"
            meta={<StatusBadge tone="success">本期可用</StatusBadge>}
            description="解读招标文件中的技术评分项、废标条款和技术要求，逐项组织目录并生成响应正文。"
            onClick={() => navigate('/planner/bids/new/setup')}
          >
            <span className="mt-md text-label-md text-primary-text">开始编写</span>
          </EntryCard>

          <Card className="border border-dashed border-border opacity-muted" surface="soft">
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>编写专项章节</CardTitle>
              <StatusBadge tone="neutral">暂未开放</StatusBadge>
            </CardHeader>
            <CardContent>
              <p className="text-body-sm text-muted-foreground">
              针对已有标书补写实施方案、技术方案、服务承诺等独立章节。
              </p>
            </CardContent>
          </Card>
        </div>
      </ShellPageContainer>
    </section>
  )
}
