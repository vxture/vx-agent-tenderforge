// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

// 标识放在哪里。
//
// 只有这一处（门禁页规范，参照 vx-agent-yucer app/(app)/lib/brand-assets.ts）：两个标识都是
// 以后会被设计稿替换的文件。换标识就是替换下面路径上的文件，路径也变时改这里一行，
// 产品里其它地方不许再写图片路径。
//
// 不进语言词典：标识不是文案，字标也不翻译——ruyin.work 在两种语言里读起来一样，
// 这正是字标的意义。

/** 公司标识，页头左侧。随设计资产一起发布。 */
export const BRAND_MARK_SRC = '/assets/brand/vxture-logo-icon.svg'

/** 公司字标，紧挨公司标识。 */
export const BRAND_WORDMARK = 'ruyin.work'

/**
 * 产品标识，每张门禁页的产品标识带里。
 *
 * 用 SVG，因为产品在四种尺寸下渲染它。同一个标识另以 `/logo.png`（512px、透明底）发布，
 * 供吃不了 SVG 的消费方（平台端会取）——产品自己不读 PNG，所以两个文件必须一起重新生成，
 * PNG 由 SVG 的同一套几何渲染，而不是描出来。两个文件都由 `scripts/render-logo.mjs` 一次写出，
 * 构图参考图在 `docs/30-design/assets/brand/`；换标识改脚本再跑，不要手改这两个文件。
 */
export const PRODUCT_MARK_SRC = '/logo.svg'
