// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15

// 公司官网，门禁页页头唯一的外链。
//
// 配置而不是写死，形状与 console-url.ts 一致：产品仓不决定公司地址；显式置空表示「不要这个链接」，
// 而不是退回某个默认值；不配置就用默认值，所以一个从没声明它的环境仍然链到真实地址。

export function websiteUrl(): string | null {
  const raw = import.meta.env.VITE_WEBSITE_URL as string | undefined
  if (raw === '') return null
  return (raw ?? 'https://vxture.com').replace(/\/$/, '')
}
