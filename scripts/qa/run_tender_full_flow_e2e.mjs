// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-06

import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { basename, resolve } from 'node:path'

const baseUrl = process.env.TENDER_E2E_BASE_URL ?? 'http://localhost:5174'
const username = process.env.TENDER_E2E_USERNAME
const password = process.env.TENDER_E2E_PASSWORD
const suppliedToken = process.env.TENDER_E2E_TOKEN ?? ''
const documentPath = process.env.TENDER_E2E_DOCUMENT
const outputDir = resolve(process.env.TENDER_E2E_OUTPUT_DIR ?? 'test/e2e-output')
const targetPages = Number(process.env.TENDER_E2E_TARGET_PAGES ?? '20')
const fullOutline = /^(1|true|yes)$/i.test(process.env.TENDER_E2E_FULL_OUTLINE ?? '')
const bidTitle = process.env.TENDER_E2E_TITLE
  ?? `TenderAgent full-flow QA ${new Date().toISOString()}`
const requestTimeoutMs = Number(process.env.TENDER_E2E_REQUEST_TIMEOUT_MS ?? '900000')

if ((!suppliedToken && (!username || !password)) || !documentPath) {
  throw new Error(
    'TENDER_E2E_TOKEN or username/password, plus TENDER_E2E_DOCUMENT, are required'
  )
}

let token = suppliedToken

async function request(path, options = {}) {
  const headers = new Headers(options.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body = options.body
  if (body !== undefined && !(body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(body)
  }
  const response = await fetch(`${baseUrl}${path}`, {
    ...options,
    headers,
    body,
    signal: AbortSignal.timeout(requestTimeoutMs),
  })
  const text = await response.text()
  let payload
  try {
    payload = JSON.parse(text)
  } catch {
    throw new Error(`${options.method ?? 'GET'} ${path} returned ${response.status}: ${text}`)
  }
  if (!response.ok || !payload.success) {
    throw new Error(
      `${options.method ?? 'GET'} ${path} failed (${response.status}/${payload.errorCode}): ${payload.message}`
    )
  }
  return payload.data
}

function log(stage, detail) {
  console.log(JSON.stringify({ at: new Date().toISOString(), stage, ...detail }))
}

async function pollWorkspace(bidId, stage, inspect, timeoutMs) {
  const startedAt = Date.now()
  let lastState = ''
  while (Date.now() - startedAt < timeoutMs) {
    const workspace = await request(`/api/bids/${bidId}`)
    const result = inspect(workspace)
    if (result.state !== lastState) {
      log(stage, { state: result.state, elapsedSeconds: Math.round((Date.now() - startedAt) / 1000) })
      lastState = result.state
    }
    if (result.error) throw new Error(`${stage} failed: ${result.error}`)
    if (result.done) return workspace
    await new Promise((resolvePoll) => setTimeout(resolvePoll, 3000))
  }
  throw new Error(`${stage} did not finish within ${Math.round(timeoutMs / 60000)} minutes`)
}

function criterionInput(item) {
  return {
    id: item.id,
    type: item.type,
    title: item.title,
    description: item.description,
    score: item.score,
    sourceExcerpt: item.sourceExcerpt,
    sourceLocator: item.sourceLocator,
    scope: item.scope,
    confidence: item.confidence,
  }
}

function slimOutline(nodes) {
  const root = nodes.find((node) => node.level === 1)
  const second = nodes.find((node) => node.level === 2 && node.parentId === root?.id)
  const third = nodes.find((node) => node.level === 3 && node.parentId === second?.id)
  if (!root || !second || !third) {
    throw new Error('generated outline does not contain a complete level 1 -> 2 -> 3 path')
  }
  return [
    {
      clientId: root.id,
      parentClientId: null,
      level: 1,
      title: root.title,
      plannedPages: targetPages,
      taskBrief: root.taskBrief,
      mustKeywords: root.mustKeywords,
      scoringPointIds: [],
    },
    {
      clientId: second.id,
      parentClientId: root.id,
      level: 2,
      title: second.title,
      plannedPages: 0,
      taskBrief: second.taskBrief,
      mustKeywords: second.mustKeywords,
      scoringPointIds: [],
    },
    {
      clientId: third.id,
      parentClientId: second.id,
      level: 3,
      title: third.title,
      plannedPages: 0,
      taskBrief: third.taskBrief,
      mustKeywords: third.mustKeywords,
      scoringPointIds: [],
    },
  ]
}

function completeOutline(nodes) {
  return nodes.map((node) => ({
    clientId: node.id,
    parentClientId: node.parentId,
    level: node.level,
    title: node.title,
    plannedPages: node.level === 1 ? node.plannedPages : 0,
    taskBrief: node.taskBrief,
    mustKeywords: node.mustKeywords,
    scoringPointIds: [],
  }))
}

function assertDepthFirstOutline(nodes) {
  const ancestors = new Map()
  for (const node of nodes) {
    if (node.level === 1) {
      if (node.parentId !== null) throw new Error(`root outline node ${node.id} has a parent`)
    } else if (node.parentId !== ancestors.get(node.level - 1)) {
      throw new Error(`outline is not depth-first at node ${node.id}`)
    }
    ancestors.set(node.level, node.id)
    for (let level = node.level + 1; level <= 3; level += 1) ancestors.delete(level)
  }
}

function assertGeneratedContent(workspace) {
  const html = workspace.chapters.map((chapter) => chapter.content ?? '').join('\n')
  const visible = html.replace(/<[^>]+>/g, ' ')
  const forbiddenPatterns = [
    /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/i,
    /\b(?:biddingMode|chapterId|criterionId|outlineNodeId|sourceLocator|generationStatus)\s*[:=]/i,
    /对应段落|本段响应|本章节响应|生成过程|编制过程|写作过程/,
  ]
  if (workspace.bid.biddingMode === 'BLIND') {
    forbiddenPatterns.push(/我方|我司|我公司|本公司/)
  }
  const leaked = forbiddenPatterns.find((pattern) => pattern.test(visible))
  if (leaked) throw new Error(`generated content contains forbidden language: ${leaked}`)

  const tableCount = (html.match(/<table\b/gi) ?? []).length
  const captionCount = (html.match(/<caption\b/gi) ?? []).length
  if (tableCount !== captionCount) {
    throw new Error(`table caption mismatch: tables=${tableCount}, captions=${captionCount}`)
  }
  if (workspace.production.reviewIssues.some((issue) => issue.severity === 'ERROR')) {
    throw new Error('final workspace still contains blocking review issues')
  }
}

async function main() {
  if (!token) {
    const login = await request('/api/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    token = login.token
    log('login', { role: login.user.roleCode })
  } else {
    log('session', { source: 'TENDER_E2E_TOKEN' })
  }

  let workspace = await request('/api/bids', {
    method: 'POST',
    body: {
      writingMethod: 'SCORING_CRITERIA',
      title: bidTitle,
      targetPages,
      biddingMode: 'BLIND',
    },
  })
  const bidId = workspace.bid.id
  log('create', { bidId, revision: workspace.bid.revision })

  const source = await readFile(documentPath)
  const form = new FormData()
  form.set('file', new Blob([source], { type: 'application/msword' }), basename(documentPath))
  workspace = await request(`/api/bids/${bidId}/source-file`, { method: 'POST', body: form })
  if (workspace.sourceFile.originalFileName !== basename(documentPath)) {
    throw new Error('uploaded source filename was not preserved')
  }
  log('upload', { bytes: source.length, fileName: workspace.sourceFile.originalFileName })

  await request(`/api/bids/${bidId}/interpretation/parse`, { method: 'POST' })
  workspace = await pollWorkspace(
    bidId,
    'interpretation',
    (current) => {
      const sourceFile = current.sourceFile
      return {
        state: `${sourceFile.parseStatus}/${sourceFile.parseStage}/${sourceFile.parseProgress}`,
        done: sourceFile.parseStatus === 'SUCCEEDED',
        error: sourceFile.parseStatus === 'FAILED' ? sourceFile.errorMessage : '',
      }
    },
    30 * 60 * 1000
  )
  const types = workspace.criteria.map((item) => item.type)
  if (
    workspace.criteria.length !== 2 ||
    types[0] !== 'PROJECT_OVERVIEW' ||
    types[1] !== 'TECHNICAL_SCORING' ||
    workspace.criteria.some((item) => !item.description?.trim())
  ) {
    throw new Error(`interpretation contract mismatch: ${JSON.stringify(types)}`)
  }
  log('interpretation-result', {
    criteriaTypes: types,
    descriptionLengths: workspace.criteria.map((item) => item.description.length),
  })

  workspace = await request(`/api/bids/${bidId}/criteria`, {
    method: 'PUT',
    body: {
      items: workspace.criteria.map(criterionInput),
      revision: workspace.bid.revision,
    },
  })
  workspace = await request(`/api/bids/${bidId}/asset-selections`, {
    method: 'PUT',
    body: { assetIds: [] },
  })
  workspace = await request(`/api/bids/${bidId}/interpretation/freeze`, {
    method: 'POST',
    body: { revision: workspace.bid.revision },
  })
  if (workspace.production.interpretationStatus !== 'FROZEN') {
    throw new Error('interpretation was not frozen')
  }
  log('interpretation-freeze', { revision: workspace.bid.revision })

  await request(`/api/bids/${bidId}/outline/generate`, { method: 'POST' })
  workspace = await pollWorkspace(
    bidId,
    'outline',
    (current) => {
      const task = current.outlineTask ?? {}
      return {
        state: `${task.status}/${task.stage}/${task.progress}`,
        done: task.status === 'SUCCEEDED',
        error: task.status === 'FAILED' ? task.errorMessage : '',
      }
    },
    30 * 60 * 1000
  )
  const rootPageTotal = workspace.outline
    .filter((node) => node.level === 1)
    .reduce((sum, node) => sum + node.plannedPages, 0)
  const nonRootPageTotal = workspace.outline
    .filter((node) => node.level > 1)
    .reduce((sum, node) => sum + node.plannedPages, 0)
  if (rootPageTotal !== targetPages || nonRootPageTotal !== 0) {
    throw new Error(
      `outline page budget mismatch: roots=${rootPageTotal}, nonRoots=${nonRootPageTotal}`
    )
  }
  assertDepthFirstOutline(workspace.outline)
  if (workspace.outline.some((node) => node.scoringPointIds.length !== 0)) {
    throw new Error('outline unexpectedly contains legacy scoring point mappings')
  }
  log('outline-result', { nodes: workspace.outline.length, rootPageTotal, nonRootPageTotal })

  const confirmedNodes = fullOutline
    ? completeOutline(workspace.outline)
    : slimOutline(workspace.outline)
  workspace = await request(`/api/bids/${bidId}/outline`, {
    method: 'PUT',
    body: { nodes: confirmedNodes, confirm: true, revision: workspace.bid.revision },
  })
  const expectedChapterCount = fullOutline
    ? workspace.outline.filter((node) => node.level === 3).length
    : 1
  if (
    workspace.production.outlineStatus !== 'FROZEN'
    || workspace.chapters.length !== expectedChapterCount
  ) {
    throw new Error(
      `outline freeze mismatch: expected ${expectedChapterCount} writable chapters, got ${workspace.chapters.length}`
    )
  }
  log('outline-freeze', { nodes: workspace.outline.length, chapters: workspace.chapters.length })

  await request(`/api/bids/${bidId}/content/generate`, { method: 'POST' })
  workspace = await pollWorkspace(
    bidId,
    'content-and-layout',
    (current) => {
      const task = current.generationTask ?? {}
      const layout = current.production?.layoutJob ?? {}
      const completed = task.completedUnits ?? 0
      const total = task.totalUnits ?? 0
      return {
        state: `${task.status}/${completed}/${total}/${layout.status ?? 'NO_LAYOUT'}`,
        done: task.status === 'SUCCEEDED',
        error: task.status === 'FAILED' ? task.errorMessage : '',
      }
    },
    120 * 60 * 1000
  )
  const task = workspace.generationTask
  const layout = workspace.production.layoutJob
  if (
    workspace.production.contentStatus !== 'FROZEN' ||
    layout?.status !== 'SUCCEEDED' ||
    layout.qaStatus !== 'PASSED' ||
    !workspace.exports.length
  ) {
    throw new Error(
      `finalization mismatch: content=${workspace.production.contentStatus}, layout=${layout?.status}, qa=${layout?.qaStatus}, exports=${workspace.exports.length}`
    )
  }
  assertGeneratedContent(workspace)
  log('content-and-layout-result', {
    units: task.totalUnits,
    chapters: workspace.chapters.length,
    actualPages: layout.actualPages,
    qaStatus: layout.qaStatus,
    exports: workspace.exports.length,
  })

  const download = await fetch(`${baseUrl}/api/bids/${bidId}/exports/latest/download`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(requestTimeoutMs),
  })
  if (!download.ok) throw new Error(`export download failed with HTTP ${download.status}`)
  const document = Buffer.from(await download.arrayBuffer())
  if (document[0] !== 0x50 || document[1] !== 0x4b) {
    throw new Error('downloaded export is not an OOXML ZIP document')
  }
  await mkdir(outputDir, { recursive: true })
  const outputPath = resolve(outputDir, `${bidId}.docx`)
  await writeFile(outputPath, document)
  const workspacePath = resolve(outputDir, `${bidId}-workspace.json`)
  await writeFile(workspacePath, JSON.stringify(workspace, null, 2), 'utf8')
  log('export', { bytes: document.length, outputPath })
  console.log(
    JSON.stringify({
      success: true,
      bidId,
      bidTitle,
      fullOutline,
      interpretationTypes: types,
      generatedOutlineNodes: workspace.outline.length,
      generationUnits: task.totalUnits,
      layoutPages: layout.actualPages,
      exportBytes: document.length,
      outputPath,
      workspacePath,
    })
  )
}

await main()
