// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-04
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'

import { EmptyState, NativeSelect } from '@vxture/design-system'

import { BidOutlineNavigation } from './components/BidOutlineNavigation'
import { BidPageShell } from './components/BidPageShell'
import { ErrorState, LoadingState } from './components/Feedback'
import { TenderChapterEditor } from './components/TenderChapterEditor'
import { buildOutlineLabels } from './outlineNumbering'
import {
  useBidChapterQuery,
  useBidGenerationProgressQuery,
  useBidMetadataQuery,
  useBidOutlineViewQuery,
  useSaveBidChapterMutation,
} from './queries'
import { buildTableNumberingContext } from './tableNumbering'

export default function BidContentPage() {
  const { bidId = '' } = useParams()
  const navigate = useNavigate()
  const metadataQuery = useBidMetadataQuery(bidId)
  const outlineQuery = useBidOutlineViewQuery(bidId)
  const progressQuery = useBidGenerationProgressQuery(bidId)
  const saveMutation = useSaveBidChapterMutation(bidId)
  const [activeChapterId, setActiveChapterId] = useState('')
  const metadata = metadataQuery.data
  const outline = outlineQuery.data
  const chapterQuery = useBidChapterQuery(bidId, activeChapterId)
  const activeChapter = chapterQuery.data?.chapter
  const outlineLabels = outline ? buildOutlineLabels(outline.nodes) : new Map<string, string>()
  const tableNumbering = useMemo(
    () => outline
      ? buildTableNumberingContext(outline.nodes, outline.chapters, activeChapterId)
      : { chapterNumber: 1, tableStart: 1, fallbackTitle: '' },
    [activeChapterId, outline],
  )

  useEffect(() => {
    if (!outline?.chapters.length) return
    if (!outline.chapters.some((chapter) => chapter.id === activeChapterId)) {
      setActiveChapterId(outline.chapters[0].id)
    }
  }, [activeChapterId, outline])

  useEffect(() => {
    if (['PENDING', 'RUNNING'].includes(progressQuery.data?.status ?? '')) {
      navigate(`/planner/bids/${bidId}/generating`, { replace: true })
    }
  }, [bidId, navigate, progressQuery.data?.status])

  if (metadataQuery.isPending || outlineQuery.isPending || progressQuery.isPending) {
    return <LoadingState label="正在读取正文" />
  }
  if (metadataQuery.error || outlineQuery.error || progressQuery.error || !metadata || !outline) {
    return (
      <ErrorState
        error={
          metadataQuery.error ?? outlineQuery.error ?? progressQuery.error ?? new Error('标书不存在')
        }
      />
    )
  }

  return (
    <BidPageShell bid={metadata.bid} current="CONTENT" fixedContent>
      <div className="flex h-full min-h-0 flex-col">
        <div className="flex min-h-0 flex-1 overflow-hidden">
          <BidOutlineNavigation
            outline={outline.nodes}
            chapters={outline.chapters}
            activeChapterId={activeChapterId}
            onSelect={setActiveChapterId}
          />
          <main className="flex min-h-0 min-w-0 flex-1 flex-col">
            <div className="shrink-0 border-b border-border bg-card p-sm md:hidden">
              <NativeSelect
                value={activeChapterId}
                onChange={(event) => setActiveChapterId(event.target.value)}
              >
                {outline.chapters.map((chapter) => (
                  <option key={chapter.id} value={chapter.id}>
                    {outlineLabels.get(chapter.outlineNodeId) ?? chapter.title}
                  </option>
                ))}
              </NativeSelect>
            </div>
            {chapterQuery.isPending ? (
              <LoadingState label="正在加载当前章节" />
            ) : chapterQuery.error ? (
              <ErrorState error={chapterQuery.error} />
            ) : activeChapter ? (
              <TenderChapterEditor
                key={activeChapter.id}
                content={activeChapter.content}
                saving={saveMutation.isPending}
                tableNumbering={tableNumbering}
                onSave={async (content) => {
                  await saveMutation.mutateAsync({
                    chapterId: activeChapter.id,
                    content,
                    revision: activeChapter.revision,
                  })
                }}
              />
            ) : (
              <EmptyState
                className="m-lg flex-1"
                icon="file-text"
                title="暂无可编辑正文章节"
              />
            )}
          </main>
        </div>
      </div>
    </BidPageShell>
  )
}
