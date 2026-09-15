// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

// zh-CN 词典：门禁页四张页面与它们的页头（门禁页规范，参照 vx-agent-yucer app/(app)/lib/messages.ts）。
//
// 组件里不出现任何中文字符串，全部从这里取；ascii-containment.test.ts 守着 src/app 目录。
// 产品其它界面还没有收敛进词典，那是另一批的事，这里不假装已经做完。

export const SHELL_TEXT = {
  brandName: '标书编写智能体',
  website: '官网',
  workspaceFallback: '当前工作区',
  // 身份块不带可见标签（owner 2026-09-16），读屏软件靠这一句知道这块是什么。
  identityCardLabel: '登录身份与当前工作区',
  noAccessTitle: '当前工作区未订阅',
  subscribeCta: '前往订阅',
  noRolesTitle: '还没有为你分配角色',
  // 说出是谁，而不是「管理员」：开通订阅的人就是本产品的管理员（平台 workspace:owner 即 ADMIN），
  // 读者因此知道该去找哪一个人。
  noRolesDescription: '请联系开通订阅的管理员为你分配角色。',
} as const

/** 未登录引导页。这个地址存在是为了让人登录，不是产品介绍页——产品主张在官网。 */
export const SIGNIN_TEXT = {
  cta: '登录',
  ariaLabel: '登录',
  title: '欢迎使用',
  // 产品自己的一句话，取自 README，不为这一页另写。
  description: '面向投标文件编制人员的智能工作台',
  // 底部信息带：产品走过的流程，按顺序，每站四个字，读起来是一个节拍而不是八个参差的词。
  // 站名对应详细设计 §1 的主流程；流程改名，站名跟着改。
  chainLabel: '全流程',
  chain: ['招标文件', '评分解读', '解读冻结', '目录规划', '正文生成', '成稿审查', '正式排版', '成果交付'],
} as const

/**
 * 已登录、当前工作区没有可用订阅。
 *
 * 与引导页是两张页面，因为读者不同：他已经通过认证，产品知道他是谁。所以这一页说出只有产品能给的
 * 两件事——谁在登录、被拒的是哪个工作区——再给订阅的路和退出的路。
 *
 * 本产品比 yucer 多三种被拒的原因（订阅失效、档位不认得、平台没答上来），各自一个状态标签、一句话、
 * 一个主动作，页面形状完全一样。
 */
export const NO_SUBSCRIPTION_TEXT = {
  badge: '未订阅',
  description: '请先完成订阅，或联系工作区管理员订阅。',
  ariaLabel: '当前工作区尚未订阅',
  signOut: '退出登录',

  lapsedTitle: '当前工作区的订阅已失效',
  lapsedDescription: '续订后即可继续使用。',
  retentionUntil: (date: string) => `已有标书的数据保留至 ${date}。`,
  renewCta: '前往续订',
  statusLabels: {
    expired: '已到期',
    cancelled: '已取消',
    suspended: '已暂停',
    overdue: '已逾期',
  } as Record<string, string>,
  lapsedBadge: (status: string) => `订阅${status}`,

  unknownTierBadge: '未识别档位',
  unknownTierTitle: '暂不支持当前订阅档位',
  unknownTierDescription: (tier: string) => `本产品还不认得档位「${tier}」，请联系管理员。`,
  viewSubscriptionCta: '查看订阅',

  unavailableBadge: '暂不可知',
  unavailableTitle: '暂时无法确认订阅状态',
  unavailableDescription: '这不代表你的订阅有问题，请稍后重试。',
  retry: '重试',
} as const

/**
 * 退出登录之后。
 *
 * 由 IdP 的退出回跳送来，落在产品根路径——与引导页同一个地址。没有这一页，产品会用「登录」回应
 * 一次主动退出，读起来像退出失败。
 */
export const SIGNED_OUT_TEXT = {
  ariaLabel: '已退出登录',
  title: '已退出登录',
  // 产品结束了自己的会话，管不了浏览器的。一句指示，不是安慰。
  description: '公用电脑上，请一并退出浏览器账号。',
  signInAgain: '重新登录',
  toConsole: '前往账号中心',
} as const

/**
 * 工作区已订阅、成员还没有角色。
 *
 * 本产品眼下没有这个状态：平台 workspace:owner 为 ADMIN，其余成员为 PLANNER，人人都有角色。
 * 页面照规范做好并在预览页可见，等产品有了「无角色」的判定就直接接上，不必再设计一次。
 */
export const NO_ROLES_TEXT = {
  badge: '无角色',
  ariaLabel: '还没有为你分配角色',
  recheck: '重新检查',
  signOut: '退出登录',
} as const

/** 门禁页预览（仅预览路由）。 */
export const GATE_PREVIEW_TEXT = {
  ariaLabel: '选择要预览的页面',
  signIn: '未登录引导页',
  noSubscription: '未订阅',
  noRoles: '无角色',
  signedOut: '已退出',
  sampleUser: '示例成员',
  sampleOrg: '示例组织',
  sampleWorkspace: '示例工作区',
} as const

/** 门禁页页头的三个外壳件。 */
export const HEADER_TEXT = {
  prefTitle: '偏好设置',
  prefLocale: '语言',
  prefTheme: '主题',
  prefThemeLight: '浅色',
  prefThemeDark: '深色',
  fullscreen: '全屏',
  fullscreenExit: '退出全屏',
} as const
