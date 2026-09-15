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
    // 缺省仍是 node：纯逻辑测试不需要 DOM，起一个 DOM 只会变慢。
    // 组件渲染测试（*.test.tsx）在文件头用 `// @vitest-environment happy-dom` 自己声明——
    // 按文件声明而不是按目录猜，一个忘了声明的渲染测试会当场报 document is not defined，而不是悄悄换了环境。
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary'],
      // 组件有了渲染测试，分母就把 .tsx 算进来：此前只统计 .ts 时页面「不在分母里」，
      // 报出来的数字看着体面，却回答不了「界面有没有被测过」。现在这个数字会先变难看——那是真实的样子。
      // 覆盖率在 CI 里只报告不设闸，判据仍是反证。
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/**/*.d.ts', 'src/main.tsx', 'src/test/**'],
    },
  },
})
