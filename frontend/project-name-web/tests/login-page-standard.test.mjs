// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-14
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = (path) => readFileSync(join(projectRoot, path), 'utf8')

/**
 * 去掉注释后的源码。「不许出现」类断言必须对它做，而不是对原文：
 * 组件注释里写着「本地账号登录（过渡通道）已移除」，对原文匹配会把一句说明
 * 当成代码判红——那条断言就不再回答「页面里有没有这个入口」。
 */
const code = (path) =>
  source(path)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:"'`])\/\/.*$/gm, '$1')

test('login page follows the organisation standard: brand, one sign-in action, hint', () => {
  const page = source('src/pages/Login/index.tsx')
  assert.match(page, /标书编写智能体/)
  assert.match(page, /请登录以验证您的订阅并访问产品/)
  assert.match(page, /登录后将自动返回当前产品/)
  assert.match(page, /\/api\/auth\/oidc\/login\?returnTo=/)
  assert.equal(
    (page.match(/className="vx-gate__action"/g) ?? []).length,
    1,
    'the standard page has exactly one action'
  )
})

test('login page offers no local password form and stores no password', () => {
  const page = code('src/pages/Login/index.tsx')
  // 本地口令入口与「记住密码」已移除；后者曾把明文密码写进 localStorage。
  for (const forbidden of [
    /type=\{?["']?password/,
    /authApi\.login/,
    /remembered-login/,
    /storage\.set/,
    /过渡通道/,
  ]) {
    assert.doesNotMatch(page, forbidden)
  }
})

/**
 * 登录页样式用到的每个 token，都必须是设计系统真正定义过的。
 *
 * CSS 把未定义的自定义属性当作失效声明而不是错误：token 一旦改名，页面照常渲染、
 * 只是颜色不对，没有构建告警也没有测试失败。所以这里沿设计系统的 CSS 导入链
 * 收集定义，而不是只读入口文件——token 大多定义在被 @import 进来的包里，只读
 * 入口会得到「全部缺失」这种看起来像结论、其实是没看见的结果。
 */
const readCssGraph = (file, seen = new Set()) => {
  if (seen.has(file)) return ''
  seen.add(file)
  const css = readFileSync(file, 'utf8')
  let out = css
  for (const [, spec] of css.matchAll(/@import\s+(?:url\()?["']([^"']+)["']/g)) {
    let next = null
    if (spec.startsWith('.')) {
      next = resolve(dirname(file), spec)
    } else {
      try {
        next = createRequire(file).resolve(spec)
      } catch {
        next = null
      }
    }
    if (next) out += readCssGraph(next, seen)
  }
  return out
}

test('every design token the login page spends is defined by the design system', () => {
  const entry = createRequire(join(projectRoot, 'package.json')).resolve(
    '@vxture/design-system/styles/globals.css'
  )
  const defined = new Set(
    [...readCssGraph(entry).matchAll(/(--[a-zA-Z0-9-]+)\s*:/g)].map((m) => m[1])
  )
  // 判据先验会不会动：没沿导入链读到足够多的定义，下面的比对就是瞎的。
  assert.ok(
    defined.size > 100,
    `only ${defined.size} tokens found - the import graph was not followed`
  )

  const used = [
    ...new Set(
      [...source('src/pages/Login/login.css').matchAll(/var\(\s*(--[a-zA-Z0-9-]+)/g)].map(
        (m) => m[1]
      )
    ),
  ]
  assert.ok(used.length > 0, 'the stylesheet spends no tokens - the check would be vacuous')
  assert.deepEqual(
    used.filter((token) => !defined.has(token)),
    [],
    'tokens used by login.css but not defined by the design system'
  )
})
