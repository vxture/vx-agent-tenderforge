// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { tenderApi } from './tender'

const client = vi.hoisted(() => ({ apiRequest: vi.fn(), downloadFile: vi.fn() }))
vi.mock('@/api/client', () => client)

/** 最近一次 apiRequest 的路径与选项。 */
const lastCall = () => {
  const [path, options] = client.apiRequest.mock.calls.at(-1) ?? []
  return { path: path as string, options: (options ?? {}) as Record<string, unknown> }
}

const query = (path: string) => new URL(path, 'http://tenderforge.test').searchParams

/**
 * 前端与后端之间的契约：路径、方法、请求体形状。
 *
 * 这些不是实现细节——后端改路径或前端拼错一个段，界面上的表现是按钮点了没反应或 404，
 * 而类型检查拦不住（路径是字符串）。游标与筛选走查询参数、写操作带 revision，都是通则写下来的约定。
 */
describe('tenderApi', () => {
  beforeEach(() => {
    client.apiRequest.mockReset()
    client.downloadFile.mockReset()
  })

  describe('列表：游标与筛选走查询参数', () => {
    it('没有参数时不留孤零零的问号', () => {
      tenderApi.listBids()
      expect(lastCall().path).toBe('/api/bids')
    })

    it('游标与条数原样带上，空白游标不带', () => {
      tenderApi.listBids({ cursor: 'c-1', limit: 20 })
      expect(lastCall().path).toBe('/api/bids?cursor=c-1&limit=20')

      tenderApi.listBids({ cursor: '   ' })
      expect(lastCall().path).toBe('/api/bids')
    })

    it('素材库的分类与关键字去掉首尾空白后作为筛选条件，缺省的不带', () => {
      tenderApi.listAssets({ category: 'TEMPLATE' as never, keyword: ' 技术方案 ', cursor: undefined })

      const params = query(lastCall().path)
      expect(lastCall().path.startsWith('/api/bid-assets?')).toBe(true)
      expect(params.get('category')).toBe('TEMPLATE')
      expect(params.get('keyword')).toBe('技术方案')
      expect(params.has('cursor')).toBe(false)
      expect(params.has('limit')).toBe(false)
    })

    it('导出历史按标书分页', () => {
      tenderApi.listExports('bid-1', { limit: 1 })
      expect(lastCall().path).toBe('/api/bids/bid-1/exports?limit=1')
    })
  })

  describe('写操作带 revision，方法与路径对得上后端', () => {
    it.each([
      ['saveCriteria', () => tenderApi.saveCriteria('bid-1', [], 7), 'PUT', '/api/bids/bid-1/criteria', { items: [], revision: 7 }],
      ['freezeInterpretation', () => tenderApi.freezeInterpretation('bid-1', 3), 'POST', '/api/bids/bid-1/interpretation/freeze', { revision: 3 }],
      ['saveOutline', () => tenderApi.saveOutline('bid-1', [], true, 4), 'PUT', '/api/bids/bid-1/outline', { nodes: [], confirm: true, revision: 4 }],
      ['freezeOutline', () => tenderApi.freezeOutline('bid-1', 5), 'POST', '/api/bids/bid-1/outline/freeze', { revision: 5 }],
      ['saveChapter', () => tenderApi.saveChapter('bid-1', 'ch-1', '<p>正文</p>', 9), 'PATCH', '/api/bids/bid-1/chapters/ch-1', { content: '<p>正文</p>', revision: 9 }],
      ['saveSetup', () => tenderApi.saveSetup('bid-1', { title: '标书', targetPages: 120, biddingMode: 'OPEN' as never, revision: 2 }), 'PATCH', '/api/bids/bid-1/setup', { title: '标书', targetPages: 120, biddingMode: 'OPEN', revision: 2 }],
    ])('%s', (_name, call, method, path, body) => {
      call()
      expect(lastCall().path).toBe(path)
      expect(lastCall().options.method).toBe(method)
      expect(lastCall().options.body).toEqual(body)
    })

    it('新建标书缺省按评分办法编写', () => {
      tenderApi.createBid({ title: '标书' } as never)
      expect(lastCall().path).toBe('/api/bids')
      expect(lastCall().options).toMatchObject({
        method: 'POST',
        body: { writingMethod: 'SCORING_CRITERIA', title: '标书' },
      })
    })

    it.each([
      ['parseSource', () => tenderApi.parseSource('bid-1'), '/api/bids/bid-1/interpretation/parse'],
      ['generateOutline', () => tenderApi.generateOutline('bid-1'), '/api/bids/bid-1/outline/generate'],
      ['generateContent', () => tenderApi.generateContent('bid-1'), '/api/bids/bid-1/content/generate'],
      ['pauseContentGeneration', () => tenderApi.pauseContentGeneration('bid-1'), '/api/bids/bid-1/content/generation/pause'],
      ['resumeContentGeneration', () => tenderApi.resumeContentGeneration('bid-1'), '/api/bids/bid-1/content/generation/resume'],
      ['createExport', () => tenderApi.createExport('bid-1'), '/api/bids/bid-1/exports'],
    ])('%s 是不带请求体的 POST', (_name, call, path) => {
      call()
      expect(lastCall().path).toBe(path)
      expect(lastCall().options.method).toBe('POST')
      expect(lastCall().options.body).toBeUndefined()
    })

    it('删除素材是 DELETE', () => {
      tenderApi.removeAsset('asset-1')
      expect(lastCall()).toEqual({ path: '/api/bid-assets/asset-1', options: { method: 'DELETE' } })
    })
  })

  describe('文件', () => {
    it('上传招标文件用表单字段 file', () => {
      const file = new File(['%PDF'], '招标文件.pdf', { type: 'application/pdf' })

      tenderApi.uploadSource('bid-1', file)

      const { path, options } = lastCall()
      expect(path).toBe('/api/bids/bid-1/source-file')
      expect(options.method).toBe('POST')
      expect((options.formData as FormData).get('file')).toBeInstanceOf(File)
    })

    it('上传素材同时带分类与文件', () => {
      const file = new File(['docx'], '范本.docx')

      tenderApi.uploadAsset('OUTLINE' as never, file)

      const form = lastCall().options.formData as FormData
      expect(lastCall().path).toBe('/api/bid-assets')
      expect(form.get('category')).toBe('OUTLINE')
      expect((form.get('file') as File).name).toBe('范本.docx')
    })

    it('按导出标识下载，文件名是「标题-成果.docx」', () => {
      tenderApi.downloadExport('bid-1', 'exp-9', '某项目投标文件')
      expect(client.downloadFile).toHaveBeenCalledWith(
        '/api/bids/bid-1/exports/exp-9/download',
        '某项目投标文件-成果.docx'
      )
    })
  })
})
