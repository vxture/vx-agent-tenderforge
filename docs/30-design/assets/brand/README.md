# 产品标识参考图

| 文件 | 是什么 |
| --- | --- |
| `logo-reference-icons8-pen-48.png` | owner 2026-09-16 给定的标识参考：Icons8「Pen」图标，Parakeet Partial Filled 风格，48px。原样保留，只改了名 |
| `logo-reference-icons8-pen-96.png` | 同上，96px |

**这两张不是正式标识，产品里没有任何地方读它们。** 正式标识是
`frontend/project-name-web/public/logo.svg` 与 `logo.png`（512，透明底），由
`frontend/project-name-web/scripts/render-logo.mjs` 按这张图的构图重画几何、一次生成两种格式——
PNG 由 SVG 的同一组数渲染，不是描图。换标识改脚本里的常量，再跑一遍：

```sh
cd frontend/project-name-web && node scripts/render-logo.mjs
```

路径常量在 `src/app/lib/brand-assets.ts`（`PRODUCT_MARK_SRC`），产品里其它地方不写图片路径。

## 授权

参考图来自 Icons8。Icons8 的许可对免费使用有署名要求，并对把图标用作 logo / 商标另有限制；
标识对外正式使用前，需要核对许可（购买授权，或由设计师在此构图上重新设计）。
