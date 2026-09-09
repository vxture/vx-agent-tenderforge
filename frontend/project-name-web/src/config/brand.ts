// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08

/**
 * 产品身份，前端侧的唯一真源。
 *
 * 与后端 `ProductIdentity.PRODUCT_CODE` 是同一个值的两处承载——前端需要它来拼
 * console 深链的 `?product=`，后端需要它走平台四通道。两处必须同时改，
 * 一次性重命名脚本负责保证这一点。
 *
 * 不要从 URL、环境变量或 OIDC client id 推导它：beta 的 client 是
 * `tenderforge-beta`，产品码仍是 `tenderforge`。
 */
export const BRAND = {
  /** 平台登记的产品码。 */
  productCode: 'tenderforge',
  /** 界面上呈现的产品名。改这个不影响任何契约。 */
  displayName: 'TenderForge',
} as const
