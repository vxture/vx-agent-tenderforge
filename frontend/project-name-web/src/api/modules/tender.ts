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

export const tenderApi = {
  listBids: () => apiRequest<BidSummary[]>('/api/bids'),
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
  listExports: (bidId: string) => apiRequest<BidExport[]>(`/api/bids/${bidId}/exports`),
  createExport: (bidId: string) =>
    apiRequest<BidExport>(`/api/bids/${bidId}/exports`, { method: 'POST' }),
  // 按标识下载：「最新」是调用方从 listExports 结果里挑出来的视角，不是一个路径段（通则 A-2）。
  downloadExport: (bidId: string, exportId: string, title: string) =>
    downloadFile(`/api/bids/${bidId}/exports/${exportId}/download`, `${title}-成果.docx`),
  listAssets: (category?: AssetCategory, keyword = '') => {
    const query = new URLSearchParams()
    if (category) query.set('category', category)
    if (keyword.trim()) query.set('keyword', keyword.trim())
    return apiRequest<BidReferenceAsset[]>(`/api/bid-assets?${query}`)
  },
  uploadAsset: (category: AssetCategory, file: File) => {
    const formData = new FormData()
    formData.set('category', category)
    formData.set('file', file)
    return apiRequest<BidReferenceAsset>('/api/bid-assets', { method: 'POST', formData })
  },
  removeAsset: (assetId: string) =>
    apiRequest<void>(`/api/bid-assets/${assetId}`, { method: 'DELETE' }),
}
