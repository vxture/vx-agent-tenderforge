// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { apiRequest, downloadFile } from '@/api/client'
import type {
  AssetCategory,
  BidChapterDetail,
  BidExport,
  BidGenerationProgress,
  BidMetadata,
  BidMode,
  BidOutlineView,
  BidReferenceAsset,
  BidSetupInput,
  BidSummary,
  BidWorkspace,
  CriterionInput,
  OutlineNodeInput,
  SectionRevisionCandidate,
  SectionRevisionMode,
} from '@/types/tender'
import type { CursorPage, PageRequest } from '@/types/page'

/** 拼查询串：空值不带，没有参数时不留一个孤零零的 `?`。 */
const queryString = (values: Record<string, string | number | null | undefined>) => {
  const query = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== null && value !== undefined && String(value).trim()) {
      query.set(key, String(value).trim())
    }
  })
  const text = query.toString()
  return text ? `?${text}` : ''
}

export const tenderApi = {
  /** 我的标书，游标分页（通则 A-3），最近更新在前。 */
  listBids: (page: PageRequest = {}) =>
    apiRequest<CursorPage<BidSummary>>(
      `/api/bids${queryString({ cursor: page.cursor, limit: page.limit })}`
    ),
  createBid: (input: BidSetupInput) =>
    apiRequest<BidWorkspace>('/api/bids', {
      method: 'POST',
      body: { writingMethod: 'SCORING_CRITERIA', ...input },
    }),
  workspace: (bidId: string) => apiRequest<BidWorkspace>(`/api/bids/${bidId}`),
  metadata: (bidId: string) => apiRequest<BidMetadata>(`/api/bids/${bidId}/metadata`),
  outline: (bidId: string) => apiRequest<BidOutlineView>(`/api/bids/${bidId}/outline`),
  generationProgress: (bidId: string) =>
    apiRequest<BidGenerationProgress>(`/api/bids/${bidId}/generation-progress`),
  chapter: (bidId: string, chapterId: string) =>
    apiRequest<BidChapterDetail>(`/api/bids/${bidId}/chapters/${chapterId}`),
  saveSetup: (
    bidId: string,
    input: { title: string; targetPages: number; biddingMode: BidMode; revision: number }
  ) => apiRequest<BidWorkspace>(`/api/bids/${bidId}/setup`, { method: 'PATCH', body: input }),
  uploadSource: (bidId: string, file: File) => {
    const formData = new FormData()
    formData.set('file', file)
    return apiRequest<BidWorkspace>(`/api/bids/${bidId}/source-file`, {
      method: 'POST',
      formData,
    })
  },
  parseSource: (bidId: string) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/interpretation/parse`, { method: 'POST' }),
  saveCriteria: (bidId: string, items: CriterionInput[], revision: number) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/criteria`, {
      method: 'PUT',
      body: { items, revision },
    }),
  freezeInterpretation: (bidId: string, revision: number) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/interpretation/freeze`, {
      method: 'POST',
      body: { revision },
    }),
  selectAssets: (bidId: string, assetIds: string[]) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/asset-selections`, {
      method: 'PUT',
      body: { assetIds },
    }),
  generateOutline: (bidId: string) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/outline/generate`, { method: 'POST' }),
  saveOutline: (bidId: string, nodes: OutlineNodeInput[], confirm: boolean, revision: number) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/outline`, {
      method: 'PUT',
      body: { nodes, confirm, revision },
    }),
  freezeOutline: (bidId: string, revision: number) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/outline/freeze`, {
      method: 'POST',
      body: { revision },
    }),
  generateContent: (bidId: string) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/content/generate`, { method: 'POST' }),
  pauseContentGeneration: (bidId: string) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/content/generation/pause`, {
      method: 'POST',
    }),
  resumeContentGeneration: (bidId: string) =>
    apiRequest<BidWorkspace>(`/api/bids/${bidId}/content/generation/resume`, {
      method: 'POST',
    }),
  saveChapter: (bidId: string, chapterId: string, content: string, revision: number) =>
    apiRequest<BidChapterDetail>(`/api/bids/${bidId}/chapters/${chapterId}`, {
      method: 'PATCH',
      body: { content, revision },
    }),
  reviseSection: (
    bidId: string,
    chapterId: string,
    input: {
      mode: SectionRevisionMode
      selectedHtml: string
      beforeContext: string
      afterContext: string
      instruction: string
      revision: number
    }
  ) =>
    apiRequest<SectionRevisionCandidate>(
      `/api/bids/${bidId}/chapters/${chapterId}/ai-revisions`,
      { method: 'POST', body: input }
    ),
  /** 成果导出，游标分页，最近生成的在前——「最新」取 limit=1 的第一条。 */
  listExports: (bidId: string, page: PageRequest = {}) =>
    apiRequest<CursorPage<BidExport>>(
      `/api/bids/${bidId}/exports${queryString({ cursor: page.cursor, limit: page.limit })}`
    ),
  createExport: (bidId: string) =>
    apiRequest<BidExport>(`/api/bids/${bidId}/exports`, { method: 'POST' }),
  // 按标识下载：「最新」是调用方从 listExports 结果里挑出来的视角，不是一个路径段（通则 A-2）。
  downloadExport: (bidId: string, exportId: string, title: string) =>
    downloadFile(`/api/bids/${bidId}/exports/${exportId}/download`, `${title}-成果.docx`),
  /** 素材库，游标分页；分类与关键字是筛选条件，走查询参数（A-2）。 */
  listAssets: (filters: { category?: AssetCategory; keyword?: string } & PageRequest = {}) =>
    apiRequest<CursorPage<BidReferenceAsset>>(
      `/api/bid-assets${queryString({
        category: filters.category,
        keyword: filters.keyword,
        cursor: filters.cursor,
        limit: filters.limit,
      })}`
    ),
  uploadAsset: (category: AssetCategory, file: File) => {
    const formData = new FormData()
    formData.set('category', category)
    formData.set('file', file)
    return apiRequest<BidReferenceAsset>('/api/bid-assets', { method: 'POST', formData })
  },
  removeAsset: (assetId: string) =>
    apiRequest<void>(`/api/bid-assets/${assetId}`, { method: 'DELETE' }),
}
