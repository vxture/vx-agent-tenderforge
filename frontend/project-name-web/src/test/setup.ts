// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { afterEach, beforeAll } from 'vitest'

/**
 * 预热 happy-dom 的 HTML 解析。
 *
 * 第一次 `innerHTML` 解析表格要加载解析器，单独跑也要约 0.8 秒，而且算在**第一个用例**头上；
 * 机器一忙（2026-09-15 三个 vitest 并行时）就被拉长到 34 秒，撞上 5 秒超时，
 * 红的是一个逻辑完全正确的用例。放进 beforeAll，这笔一次性开销不再冒充某个用例的失败。
 */
beforeAll(() => {
  if (typeof document === 'undefined') return
  document.createElement('div').innerHTML = '<table><tr><td>预热</td></tr></table>'
})

/**
 * 渲染测试之间卸载组件。
 *
 * vitest 不开 globals，Testing Library 的自动清理不会挂上——不清理，前一个用例渲染出的
 * 订阅页会留在 document 里，后一个用例的 `getByText` 可能恰好找到它而误判通过。
 * 纯逻辑测试跑在 node 环境、没有 document，这里直接跳过，也就不必为它们加载 react-dom。
 */
afterEach(async () => {
  if (typeof document === 'undefined') return
  const { cleanup } = await import('@testing-library/react')
  cleanup()
})
