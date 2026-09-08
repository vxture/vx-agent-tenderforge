// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-08
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

/**
 * 单元测试与覆盖率。
 *
 * 与 `tests/*.test.mjs`（node:test）分工不同，两者都保留：
 * 那一组做的是**源码文本断言**——「这个页面用了 DataTable 而不是裸 table」这类
 * 仓内约定，它们不执行应用代码，所以对它们统计覆盖率没有意义。
 * 这一组执行真实模块，覆盖率数字来自它。
 *
 * 刻意不复用 vite.config.ts：那份配置带着 tailwind 与 react 插件、
 * dev server 代理和 rollup 分包规则，全部与跑测试无关，
 * 引进来只会让一次单元测试为了构建 CSS 而变慢，并且在插件报错时把测试也拖红。
 * 这里只保留唯一真正共享的东西——路径别名。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary'],
      include: ['src/**/*.ts'],
      // 只统计被测的纯逻辑层。页面与组件没有渲染测试，
      // 把它们算进分母只会得到一个被稀释到无法解读的数字，
      // 而那个数字既不能说明契约层是否被保护，也不能推动任何决定。
      exclude: ['src/**/*.test.ts', 'src/**/*.d.ts', 'src/main.tsx'],
    },
  },
})
