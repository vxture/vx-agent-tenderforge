// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { tenderApi } from '@/api/modules/tender'
import type {
  AssetCategory,
  BidMode,
  BidSetupInput,
  BidWorkspace,
  CriterionInput,
  OutlineNodeInput,
  SectionRevisionMode,
} from '@/types/tender'

export const tenderKeys = {
  bids: ['tender-bids'] as const,
  workspace: (bidId: string) => ['tender-workspace', bidId] as const,
  metadata: (bidId: string) => ['tender-metadata', bidId] as const,
  outline: (bidId: string) => ['tender-outline', bidId] as const,
  progress: (bidId: string) => ['tender-generation-progress', bidId] as const,
  chapter: (bidId: string, chapterId: string) =>
    ['tender-chapter', bidId, chapterId] as const,
  assets: (category?: AssetCategory, keyword = '') => ['tender-assets', category, keyword] as const,
}

export const useBidsQuery = () =>
  useQuery({
    queryKey: tenderKeys.bids,
    queryFn: tenderApi.listBids,
    refetchInterval: (query) =>
      query.state.data?.some((bid) =>
        ['PARSING', 'OUTLINE_GENERATING', 'GENERATING', 'LAYOUT_QUEUED', 'LAYOUT_RUNNING'].includes(
          bid.status
        )
      )
        ? 3_000
        : false,
  })

export const useBidWorkspaceQuery = (bidId: string) =>
  useQuery({
    queryKey: tenderKeys.workspace(bidId),
    queryFn: () => tenderApi.workspace(bidId),
    enabled: Boolean(bidId),
    refetchInterval: (query) => {
      const workspace = query.state.data
      const parsing = workspace?.sourceFile?.parseStatus === 'PARSING'
      const outlining = ['PENDING', 'RUNNING'].includes(workspace?.outlineTask?.status ?? '')
      return parsing || outlining ? 1_500 : false
    },
    refetchIntervalInBackground: true,
  })

export const useBidMetadataQuery = (bidId: string) =>
  useQuery({
    queryKey: tenderKeys.metadata(bidId),
    queryFn: () => tenderApi.metadata(bidId),
    enabled: Boolean(bidId),
  })

export const useBidOutlineViewQuery = (bidId: string) =>
  useQuery({
    queryKey: tenderKeys.outline(bidId),
    queryFn: () => tenderApi.outline(bidId),
    enabled: Boolean(bidId),
  })

export const useBidGenerationProgressQuery = (bidId: string) =>
  useQuery({
    queryKey: tenderKeys.progress(bidId),
    queryFn: () => tenderApi.generationProgress(bidId),
    enabled: Boolean(bidId),
    refetchInterval: (query) =>
      ['PENDING', 'RUNNING'].includes(query.state.data?.status ?? '') ? 1_500 : false,
  })

export const useBidChapterQuery = (bidId: string, chapterId: string) =>
  useQuery({
    queryKey: tenderKeys.chapter(bidId, chapterId),
    queryFn: () => tenderApi.chapter(bidId, chapterId),
    enabled: Boolean(bidId && chapterId),
  })

export const useTenderAssetsQuery = (category?: AssetCategory, keyword = '') =>
  useQuery({
    queryKey: tenderKeys.assets(category, keyword),
    queryFn: () => tenderApi.listAssets(category, keyword),
  })

const metadataFromWorkspace = (workspace: BidWorkspace) => ({
  bid: workspace.bid,
  sourceFile: workspace.sourceFile,
  outlineTask: workspace.outlineTask,
  generationTask: workspace.generationTask,
  selectedAssetIds: workspace.selectedAssetIds,
  exports: workspace.exports,
  production: workspace.production,
})

const useWorkspaceMutation = <TInput>(
  bidId: string,
  mutationFn: (input: TInput) => Promise<BidWorkspace>
) => {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn,
    onSuccess: (workspace) => {
      queryClient.setQueryData(tenderKeys.workspace(bidId), workspace)
      queryClient.setQueryData(tenderKeys.metadata(bidId), metadataFromWorkspace(workspace))
      void queryClient.invalidateQueries({ queryKey: tenderKeys.metadata(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.outline(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.progress(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.bids })
    },
  })
}

export function useCreateBidMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: BidSetupInput) => tenderApi.createBid(input),
    onSuccess: (workspace) => {
      queryClient.setQueryData(tenderKeys.workspace(workspace.bid.id), workspace)
      queryClient.setQueryData(
        tenderKeys.metadata(workspace.bid.id),
        metadataFromWorkspace(workspace)
      )
      void queryClient.invalidateQueries({ queryKey: tenderKeys.bids })
    },
  })
}

export const useSaveSetupMutation = (bidId: string) =>
  useWorkspaceMutation(
    bidId,
    (input: { title: string; targetPages: number; biddingMode: BidMode; revision: number }) =>
      tenderApi.saveSetup(bidId, input)
  )

export const useUploadSourceMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, (file: File) => tenderApi.uploadSource(bidId, file))

export const useParseSourceMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, () => tenderApi.parseSource(bidId))

export const useSaveCriteriaMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, (input: { items: CriterionInput[]; revision: number }) =>
    tenderApi.saveCriteria(bidId, input.items, input.revision)
  )

export const useFreezeInterpretationMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, (revision: number) =>
    tenderApi.freezeInterpretation(bidId, revision)
  )

export const useSelectAssetsMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, (assetIds: string[]) => tenderApi.selectAssets(bidId, assetIds))

export const useGenerateOutlineMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, () => tenderApi.generateOutline(bidId))

export const useSaveOutlineMutation = (bidId: string) =>
  useWorkspaceMutation(
    bidId,
    (input: { nodes: OutlineNodeInput[]; confirm: boolean; revision: number }) =>
      tenderApi.saveOutline(bidId, input.nodes, input.confirm, input.revision)
  )

export const useGenerateContentMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, () => tenderApi.generateContent(bidId))

export const usePauseContentGenerationMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, () => tenderApi.pauseContentGeneration(bidId))

export const useResumeContentGenerationMutation = (bidId: string) =>
  useWorkspaceMutation(bidId, () => tenderApi.resumeContentGeneration(bidId))

export function useSaveBidChapterMutation(bidId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { chapterId: string; content: string; revision: number }) =>
      tenderApi.saveChapter(bidId, input.chapterId, input.content, input.revision),
    onSuccess: (detail) => {
      queryClient.setQueryData(tenderKeys.chapter(bidId, detail.chapter.id), detail)
      void queryClient.invalidateQueries({ queryKey: tenderKeys.metadata(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.outline(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.bids })
    },
  })
}

export const useReviseBidSectionMutation = (bidId: string) =>
  useMutation({
    mutationFn: (input: {
      chapterId: string
      mode: SectionRevisionMode
      selectedHtml: string
      beforeContext: string
      afterContext: string
      instruction: string
      revision: number
    }) => tenderApi.reviseSection(bidId, input.chapterId, input),
  })

export function useCreateBidExportMutation(bidId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => tenderApi.createExport(bidId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: tenderKeys.workspace(bidId) })
      void queryClient.invalidateQueries({ queryKey: tenderKeys.bids })
    },
  })
}

export function useUploadTenderAssetMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ category, file }: { category: AssetCategory; file: File }) =>
      tenderApi.uploadAsset(category, file),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['tender-assets'] }),
  })
}

export function useRemoveTenderAssetMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: tenderApi.removeAsset,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['tender-assets'] }),
  })
}
