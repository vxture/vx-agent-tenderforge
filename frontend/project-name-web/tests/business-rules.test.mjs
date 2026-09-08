// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-02
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = (path) => readFileSync(join(projectRoot, path), 'utf8')

const sourceFiles = (directory) =>
  readdirSync(directory).flatMap((name) => {
    const path = join(directory, name)
    return statSync(path).isDirectory() ? sourceFiles(path) : [path]
  })

test('planner navigation contains exactly the four TenderAgent modules', () => {
  const menu = source('src/layouts/menuConfig.ts')
  for (const label of ['标书写作', '素材管理', '我的标书', '用户管理']) {
    assert.match(menu, new RegExp(`title: '${label}'`))
  }
  assert.equal((menu.match(/path: '\/planner\//g) ?? []).length, 4)
})

test('writing router exposes each workflow page', () => {
  const router = source('src/router/index.tsx')
  for (const step of ['setup', 'interpretation', 'outline', 'generating', 'content']) {
    assert.match(router, new RegExp(`planner/bids/:bidId/${step}`))
  }
  assert.doesNotMatch(router, /workbench|console\/overview/)
})

test('lazy routes recover from stale deployment chunks with a custom error boundary', () => {
  const router = source('src/router/index.tsx')
  const errorPage = source('src/router/RouteErrorPage.tsx')

  assert.match(router, /errorElement: <RouteErrorPage \/>/)
  assert.match(router, /const hydrateFallbackElement = \([\s\S]*?<ShellBootScreen/)
  assert.match(router, /path: '\/'[\s\S]*?hydrateFallbackElement/)
  assert.equal((router.match(/hydrateFallbackElement,/g) ?? []).length, 7)
  assert.match(errorPage, /Failed to fetch dynamically imported module/)
  assert.match(errorPage, /sessionStorage\.setItem/)
  assert.match(errorPage, /window\.location\.reload\(\)/)
  assert.match(errorPage, /CHUNK_RELOAD_WINDOW_MS = 30_000/)
})

test('workflow client IDs work on non-secure HTTP origins', () => {
  const clientId = source('src/utils/clientId.ts')
  const interpretation = source('src/features/tender/BidInterpretationPage.tsx')
  const outline = source('src/features/tender/BidOutlinePage.tsx')

  assert.match(clientId, /typeof webCrypto\?\.randomUUID === 'function'/)
  assert.match(clientId, /getRandomValues/)
  assert.match(clientId, /Math\.random/)
  assert.match(interpretation, /id: createClientId\(\)/)
  assert.match(outline, /clientId: createClientId\(\)/)
  assert.doesNotMatch(interpretation, /crypto\.randomUUID/)
  assert.doesNotMatch(outline, /crypto\.randomUUID/)
})

test('starting a bid opens local setup and creates only when setup is saved', () => {
  const methodPage = source('src/features/tender/WritingMethodPage.tsx')
  const setupPage = source('src/features/tender/BidSetupPage.tsx')
  const queries = source('src/features/tender/queries.ts')

  assert.match(methodPage, /navigate\('\/planner\/bids\/new\/setup'\)/)
  assert.doesNotMatch(methodPage, /useCreateBidMutation|createBid/)
  assert.match(setupPage, /const isNew = !bidId/)
  assert.match(setupPage, /createMutation\.mutateAsync\(\{/)
  assert.match(setupPage, /title: title\.trim\(\)/)
  assert.match(setupPage, /targetPages/)
  assert.match(setupPage, /biddingMode: mode/)
  assert.match(setupPage, /created\.bid\.id}\/interpretation/)
  assert.match(queries, /mutationFn: \(input: BidSetupInput\) => tenderApi\.createBid\(input\)/)
})

test('bid workflow uses a standalone full-screen shell outside the product layout', () => {
  const router = source('src/router/index.tsx')
  const shell = source('src/features/tender/components/BidPageShell.tsx')

  assert.match(shell, /h-dvh/)
  assert.match(shell, /<header[\s\S]*?<BidStepBar[\s\S]*?<\/header>/)
  assert.match(
    router,
    /path: '\/planner\/bids\/new\/setup',[\s\S]*?lazy: lazyProtectedPage\(\(\) => import\('@\/features\/tender\/BidSetupPage'\)\)/
  )
  assert.match(router, /const lazyProtectedPage[\s\S]*?<AuthGuard>[\s\S]*?<Page \/>/)
  assert.ok(router.indexOf("path: '/planner/bids/new/setup'") > router.indexOf("path: 'admin/*'"))
  assert.doesNotMatch(shell, /MainLayout|<Header|<Menu/)
})

test('bid workflow contains overflow in one viewport and one vertical scroller', () => {
  const shell = source('src/features/tender/components/BidPageShell.tsx')

  assert.match(shell, /h-dvh[^"]*w-full[^"]*min-w-0[^"]*overflow-hidden/)
  assert.match(shell, /overflow-x-hidden overflow-y-auto overscroll-contain/)
  assert.doesNotMatch(shell, /flex-1 overflow-y-auto'/)
})

test('top-level writing pages avoid duplicate creation and bid-list shortcuts', () => {
  const writing = source('src/features/tender/WritingMethodPage.tsx')
  const bids = source('src/features/tender/BidsPage.tsx')

  assert.doesNotMatch(writing, /navigate\('\/planner\/bids'\)/)
  assert.doesNotMatch(bids, /新建标书/)
})

test('setup page accepts four-digit page targets and bid status stays business-facing', () => {
  const setup = source('src/features/tender/BidSetupPage.tsx')
  const bids = source('src/features/tender/BidsPage.tsx')

  assert.match(setup, /id="target-pages"[\s\S]*?className="w-24 min-w-24"/)
  assert.match(setup, /onFocus=\{\(event\) => event\.currentTarget\.select\(\)\}/)
  assert.match(setup, /max=\{2000\}/)
  assert.doesNotMatch(bids, /排版 \{bid\.layoutStatus\}/)
  assert.doesNotMatch(bids, /QA \{bid\.qaStatus/)
  assert.doesNotMatch(bids, /bid\.actualPages/)
})

test('content generation has a dedicated progress page before the editor', () => {
  const outline = source('src/features/tender/BidOutlinePage.tsx')
  const generating = source('src/features/tender/BidGeneratingPage.tsx')
  const content = source('src/features/tender/BidContentPage.tsx')

  assert.match(outline, /navigate\(`\/planner\/bids\/\$\{bidId}\/generating`\)/)
  assert.match(generating, /task\?\.status === 'SUCCEEDED'/)
  assert.match(
    generating,
    /navigate\(`\/planner\/bids\/\$\{bidId}\/content`, \{ replace: true \}\)/
  )
  assert.match(generating, /generationPresentation\(task\)/)
  assert.match(generating, /AUTO_REVIEW_STARTED/)
  assert.match(generating, /LAYOUT_STARTED/)
  assert.match(generating, /h-full overflow-y-auto px-page-inset/)
  assert.match(generating, /min-h-full[^\"]*flex-col[^\"]*justify-center/)
  assert.match(generating, /max-h-96[^\"]*overflow-y-auto[^\"]*overscroll-contain/)
  assert.match(generating, /w-full max-w-content-narrow-lg/)
  assert.match(generating, /<ConfirmDestructive/)
  assert.match(generating, /停止任务/)
  assert.match(generating, /继续未完成部分/)
  assert.ok(generating.indexOf('停止任务') < generating.indexOf('<CardTitle>运行事件</CardTitle>'))
  assert.match(generating, /usePauseContentGenerationMutation\(bidId\)/)
  assert.match(generating, /useResumeContentGenerationMutation\(bidId\)/)
  assert.doesNotMatch(generating, /flex h-full items-center justify-center overflow-y-auto/)
  assert.doesNotMatch(generating, /max-h-panel-md/)
  assert.doesNotMatch(content, /style=\{\{ width: `\$\{task\.progress}%` \}\}/)
  assert.match(generating, /useBidGenerationProgressQuery\(bidId\)/)
})

test('outline editor does not expose legacy scoring coverage controls', () => {
  const outline = source('src/features/tender/BidOutlinePage.tsx')

  assert.doesNotMatch(outline, /<select/)
  assert.doesNotMatch(outline, /selectedOptions/)
  assert.match(outline, /scoringPointIds: \[\]/)
  assert.doesNotMatch(outline, /validScoringPointIds|normalizeScoringPointIds/)
  assert.doesNotMatch(outline, /评分点覆盖/)
  assert.doesNotMatch(outline, /normalizeLeafCoverage/)
})

test('interpretation page uses a single-column form with the source file and materials in flow', () => {
  const page = source('src/features/tender/BidInterpretationPage.tsx')
  const sourceSection = page.indexOf('<CardTitle>招标文件</CardTitle>')
  const sourceFileName = page.indexOf('workspace.sourceFile?.originalFileName')
  const uploadAction = page.indexOf("workspace.sourceFile ? '重新上传' : '上传文件'")
  const resultSection = page.indexOf('<CardTitle>解读结果</CardTitle>')
  const materialsSection = page.indexOf('<CardTitle>参考素材</CardTitle>')

  assert.ok(sourceSection < sourceFileName)
  assert.ok(sourceFileName < uploadAction)
  assert.ok(uploadAction < resultSection)
  assert.ok(resultSection < materialsSection)
  assert.doesNotMatch(page, /xl:grid-cols-\[minmax\(0,1fr\)_300px\]/)
  assert.doesNotMatch(page, /<aside/)
  for (const label of ['项目概述', '技术部分评分要求', '字符']) {
    assert.match(page, new RegExp(label))
  }
  assert.match(page, /type: 'PROJECT_OVERVIEW'/)
  assert.match(page, /type: 'TECHNICAL_SCORING'/)
  assert.doesNotMatch(page, /type: 'REJECTION'/)
  assert.doesNotMatch(page, /type: 'FACT'/)
})

test('interpretation refresh resumes server-side progress without resubmitting the task', () => {
  const page = source('src/features/tender/BidInterpretationPage.tsx')
  const queries = source('src/features/tender/queries.ts')

  assert.match(queries, /workspace\?\.sourceFile\?\.parseStatus === 'PARSING'/)
  assert.match(queries, /workspace\?\.outlineTask\?\.status/)
  assert.match(queries, /return parsing \|\| outlining \? 1_500 : false/)
  assert.match(queries, /refetchIntervalInBackground: true/)
  assert.match(page, /const isParsing = workspace\.sourceFile\?\.parseStatus === 'PARSING'/)
  assert.match(page, /任务在后台持续执行，可以刷新页面或稍后返回/)
  assert.match(page, /isParsing\s*\? '解析进行中'/)
  assert.doesNotMatch(page, /useEffect\([\s\S]{0,300}parseMutation\.mutate/)
})

test('interpretation exposes object checkpoints and retries only failed output', () => {
  const page = source('src/features/tender/BidInterpretationPage.tsx')
  const types = source('src/types/tender.ts')
  const status = source('src/features/tender/components/InterpretationObjectStatus.tsx')

  for (const field of [
    'overviewStatus',
    'overviewErrorMessage',
    'scoringStatus',
    'scoringErrorMessage',
  ]) {
    assert.match(types, new RegExp(field))
  }
  assert.match(page, /hasPartialSuccess/)
  assert.match(page, /仅重试失败对象/)
  assert.match(page, /dirtyObjects/)
  assert.match(page, /<InterpretationObjectStatus status=\{objectStatus\}/)
  for (const label of ['等待生成', '生成中', '已完成', '生成失败']) {
    assert.match(status, new RegExp(label))
  }
})

test('outline generation is a refresh-safe server-side task', () => {
  const page = source('src/features/tender/BidOutlinePage.tsx')

  assert.match(page, /const outlineTask = workspace\.outlineTask/)
  assert.match(page, /\['PENDING', 'RUNNING'\]\.includes/)
  assert.match(page, /任务在后台持续执行，可以刷新页面或稍后返回/)
  assert.match(page, /outlineStageLabels\[outlineTask\.stage\]/)
  assert.match(page, /disabled=\{generateMutation\.isPending \|\| isGenerating\}/)
  assert.match(page, /setNodes\(\[\]\)/)
  assert.match(page, /\['PENDING', 'RUNNING', 'FAILED'\]\.includes/)
  assert.match(page, /node\.level === 1/)
  assert.match(page, /legacyPages\.get\(node\.clientId\)/)
  assert.match(page, /旧目录已清除/)
  assert.doesNotMatch(page, /normalizeLeafCoverage/)
  assert.match(page, /项目概述、技术评分要求和参考素材/)
})

test('content editor combines the complete three-level outline with a document canvas', () => {
  const content = source('src/features/tender/BidContentPage.tsx')
  const navigation = source('src/features/tender/components/BidOutlineNavigation.tsx')
  const editor = source('src/features/tender/components/TenderChapterEditor.tsx')

  assert.match(content, /<BidOutlineNavigation/)
  assert.match(content, /outline=\{outline\.nodes\}/)
  assert.match(content, /useBidChapterQuery\(bidId, activeChapterId\)/)
  assert.match(content, /<TenderChapterEditor/)
  for (const action of ['重新生成', '生成成果', '下载最新成果', '正文查看与编辑']) {
    assert.ok(!content.includes(action), `content workspace should not render ${action}`)
  }
  assert.match(navigation, /outline\.map/)
  assert.match(navigation, /node\.level === 1/)
  assert.match(navigation, /node\.level === 2/)
  assert.match(navigation, /node\.level === 3/)
  assert.match(navigation, /w-96[^"]*lg:w-\[432px\][^"]*xl:w-\[480px\]/)
  assert.match(navigation, /whitespace-normal[^"]*break-words/)
  assert.match(editor, /report-editor-page/)
  assert.match(editor, /max-w-\[900px\]/)
})

test('outline tree supports folding and the editor exposes only undo, redo and configurable table insertion', () => {
  const navigation = source('src/features/tender/components/BidOutlineNavigation.tsx')
  const editor = source('src/features/tender/components/TenderChapterEditor.tsx')
  const tableNormalization = source('src/features/tender/components/editorTableNormalization.ts')
  const tableLabels = source('src/features/tender/components/TableLabelsExtension.ts')

  assert.match(navigation, /role="tree"/)
  assert.match(navigation, /aria-expanded=/)
  assert.match(navigation, /onToggle\(node\.id\)/)
  assert.match(navigation, /buildOutlineTree/)
  assert.match(navigation, /buildOutlineLabels/)
  assert.match(editor, /TableKit\.configure/)
  assert.match(editor, /createTableBundle\(rows, columns\)/)
  assert.match(editor, /MAX_TABLE_ROWS = 30/)
  assert.match(editor, /MAX_TABLE_COLUMNS = 20/)
  assert.match(editor, /label="(?:\u884c\u6570|琛屾暟)"/)
  assert.match(editor, /label="(?:\u5217\u6570|鍒楁暟)"/)
  assert.match(editor, /TableTitle/)
  assert.match(editor, /TableNote/)
  assert.match(editor, /normalizeEditorContent/)
  assert.match(editor, /parseInlineTable/)
  assert.match(editor, /createTableElements/)
  assert.match(editor, /splitNumberedParagraph/)
  assert.match(editor, /normalizeEditorTables\(container, tableNumbering\.fallbackTitle\)/)
  assert.match(editor, /numberEditorTables\(container, tableNumbering\)/)
  assert.match(editor, /prepareEditorContent/)
  assert.match(
    editor,
    /replaceTableLabelTags\(container, 'p\[data-table-title\]', 'tender-table-title'\)/
  )
  assert.match(
    editor,
    /replaceTableLabelTags\(container, 'p\[data-table-note\]', 'tender-table-note'\)/
  )
  assert.match(editor, /const normalized = normalizeEditorContent\(editor\.getHTML\(\), tableNumbering\)/)
  assert.match(tableNormalization, /querySelectorAll\('caption'\)/)
  assert.match(tableNormalization, /repairTitleCell/)
  assert.match(tableNormalization, /precedingTitleParagraphs/)
  assert.match(tableNormalization, /titleNodes\.slice\(1\).*duplicate\.remove\(\)/)
  assert.match(tableNormalization, /data-table-title/)
  assert.match(tableNormalization, /data-table-note/)
  assert.match(tableNormalization, /buildFallbackTitle/)
  assert.match(tableNormalization, /formatTableTitle/)
  assert.match(tableLabels, /priority: 1_000/)
  assert.match(tableLabels, /'tender-table-title'/)
  assert.match(tableLabels, /'tender-table-note'/)
  assert.match(tableLabels, /tag: `p\[\$\{attribute\}="true"\]`/)
  assert.match(tableLabels, /data-placeholder/)
  const styles = source('src/styles/globals.css')
  assert.match(styles, /p\[data-table-title\]/)
  assert.match(styles, /p\[data-table-note\]/)
  assert.doesNotMatch(styles, /p:has\(\+ \.tableWrapper\)/)
  assert.doesNotMatch(styles, /\.tableWrapper \+ p/)
  assert.doesNotMatch(editor, /from '.\/FlowDiagramExtension'/)
  assert.match(editor, /icon="undo"/)
  assert.match(editor, /icon="refresh"/)
  assert.match(editor, /name="table"/)
  assert.match(editor, /name="save"/)
  assert.doesNotMatch(editor, /lucide-react/)
  for (const removedTool of [
    'toggleBold',
    'toggleItalic',
    'toggleStrike',
    'toggleBulletList',
    'toggleOrderedList',
    'toggleBlockquote',
    'setHeading',
    'Paintbrush',
    'FormatSnapshot',
    'mergeOrSplit',
    'onRevise',
  ]) {
    assert.ok(!editor.includes(removedTool), `editor should not expose ${removedTool}`)
  }
})

test('writing page presents scoring-based and specialist modes', () => {
  const page = source('src/features/tender/WritingMethodPage.tsx')
  assert.match(page, /按招标评分点写/)
  assert.match(page, /编写专项章节/)
  assert.match(page, /暂未开放/)
})

test('step bar preserves the requested four-stage order', () => {
  const stepBar = source('src/features/tender/components/BidStepBar.tsx')
  const labels = ['标书设置', '招标文件解读', '目录编写', '正文编写']
  let previous = -1
  for (const label of labels) {
    const index = stepBar.indexOf(label)
    assert.ok(index > previous, `${label} should appear after the previous step`)
    previous = index
  }
})

test('tender API covers setup, parsing, outline, content, export and assets', () => {
  const api = source('src/api/modules/tender.ts')
  for (const path of [
    '/api/bids',
    '/interpretation/parse',
    '/criteria',
    '/outline/generate',
    '/content/generate',
    '/exports',
    '/api/bid-assets',
  ]) {
    assert.ok(api.includes(path), `missing API path ${path}`)
  }
})

test('user management is limited to the current profile, avatar and password', () => {
  const accountApi = source('src/api/modules/auth.ts')
  assert.match(accountApi, /\/api\/account\/profile/)
  assert.match(accountApi, /\/api\/account\/avatar/)
  assert.match(accountApi, /\/api\/account\/password/)
  assert.doesNotMatch(accountApi, /\/api\/admin\/users/)

  const page = source('src/pages/PlannerAccount/index.tsx')
  assert.match(page, /features\/account\/AccountPage/)
})

test('TenderAgent branding uses Funnel Display', () => {
  assert.match(source('src/main.tsx'), /@vxture\/design-system\/styles\/globals\.css/)
  assert.match(source('src/main.tsx'), /@vxture\/design-system\/styles\/brands\/vxture\.css/)
  assert.doesNotMatch(source('src/styles/globals.css'), /Funnel Display Variable/)
  assert.match(source('src/pages/Login/index.tsx'), /TenderAgent/)
  assert.match(source('src/layouts/Header.tsx'), /TenderAgent/)
})

test('global header exposes branded search, three real tools and the account panel', () => {
  const app = source('src/App.tsx')
  const header = source('src/layouts/Header.tsx')
  const layout = source('src/layouts/MainLayout.tsx')
  const logo = source('public/assets/brand/vxture-logo-icon.svg')

  assert.match(logo, /aria-label="Vxture"/)
  assert.match(header, /logoSrc="\/assets\/brand\/vxture-logo-icon\.svg"/)
  assert.match(header, /<ShellSearchBox/)
  assert.match(header, /getMenuListByPortal/)
  assert.match(header, /<ShellIconGroup label="页面工具">/)
  assert.match(header, /<ShellIconButton[\s\S]*?icon="sidebar"/)
  assert.match(header, /<ShellThemeToggle/)
  assert.match(header, /<ShellFullscreenToggle/)
  assert.match(header, /<ShellUserMenu/)
  assert.match(header, /<ShellPreferencePanel/)
  assert.match(header, /statusTag: \{ label: '已登录', verified: true \}/)
  assert.match(app, /<FullscreenProvider>/)
  assert.match(layout, /<ShellViewport/)
  assert.match(layout, /id="tenderagent-app-shell"/)
})

test('audit log translates every current business audit action', () => {
  const page = source('src/features/admin/AuditLogsPage.tsx')
  const actions = [
    'AUTH_LOGIN',
    'AUTH_LOGOUT',
    'BID_CREATE',
    'BID_SETUP_UPDATE',
    'BID_SOURCE_UPLOAD',
    'BID_SOURCE_PARSE_SUBMIT',
    'BID_SOURCE_PARSE',
    'BID_CRITERIA_UPDATE',
    'BID_INTERPRETATION_FREEZE',
    'BID_OUTLINE_GENERATE_SUBMIT',
    'BID_OUTLINE_GENERATE',
    'BID_OUTLINE_UPDATE',
    'BID_OUTLINE_FREEZE',
    'BID_CONTENT_GENERATE',
    'BID_CONTENT_PAUSE',
    'BID_CONTENT_RESUME',
    'BID_CONTENT_REVIEW',
    'BID_CHAPTER_UPDATE',
    'BID_SECTION_AI_CANDIDATE',
    'BID_CONTENT_FREEZE',
    'BID_LAYOUT_START',
    'BID_LAYOUT_COMPLETE',
    'BID_EXPORT_CREATE',
    'ACCOUNT_PROFILE_UPDATE',
    'ACCOUNT_AVATAR_UPDATE',
    'ACCOUNT_PASSWORD_CHANGE',
    'USER_CREATE',
    'USER_UPDATE',
    'USER_ENABLE',
    'USER_DISABLE',
  ]

  for (const action of actions) {
    assert.match(page, new RegExp(`\\b${action}:`), `missing audit label for ${action}`)
  }
})

test('session recovery and standard lists use public design-system patterns', () => {
  const guard = source('src/router/AuthGuard.tsx')
  const users = source('src/features/admin/UsersPage.tsx')
  const audit = source('src/features/admin/AuditLogsPage.tsx')
  const bids = source('src/features/tender/BidsPage.tsx')
  const assets = source('src/features/tender/TenderAssetsPage.tsx')

  assert.match(guard, /<ShellBootScreen/)
  for (const page of [users, audit, bids, assets]) {
    assert.match(page, /<ListPageTemplate/)
    assert.match(page, /<DataTable/)
    assert.match(page, /<Pagination/)
    assert.doesNotMatch(page, /<Table(?:\s|>)/)
  }
  for (const page of [users, bids, assets]) {
    assert.match(page, /<ActionMenu/)
  }
  assert.doesNotMatch(users, /AdminPagination|SectionShell/)
  assert.doesNotMatch(audit, /AdminPagination|SectionShell/)
})

test('runtime frontend contains no village-planning residue', () => {
  const forbidden = /村庄规划|规划编制员|village-planning|villagePlanning|PlanningProject/
  const matches = sourceFiles(join(projectRoot, 'src'))
    .filter((path) => /\.(ts|tsx|css|html)$/.test(path))
    .filter((path) => forbidden.test(readFileSync(path, 'utf8')))
  assert.deepEqual(matches, [])
})
