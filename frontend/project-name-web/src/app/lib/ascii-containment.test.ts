// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

// 门禁页的文案只在词典里（门禁页规范；参照 vx-agent-yucer app/(app)/lib/ascii-containment.test.ts）。
//
// 守的是会被读者看到的文字：带中日韩字符的字符串字面量只允许出现在两份词典里。注释是写给维护者的
// 说明，不扫。范围是 src/app——门禁页、词典与它们的机制都在这里；产品其它界面还没收敛进词典，
// 把它们也扫进来这条守卫第一天就是红的，红着的守卫没人看。
//
// 用 import.meta.glob 读源码而不是 node:fs：前端 tsconfig 不带 Node 类型，这样守卫本身也在类型检查之内。

const SOURCES = import.meta.glob(['../**/*.ts', '../**/*.tsx', '!../**/*.test.ts', '!../**/*.test.tsx'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

// glob 的键相对这个文件：同目录的是 ./messages.ts，别处的是 ../components/x.tsx。统一成相对 src/app 的路径。
const FILES = Object.fromEntries(
  Object.entries(SOURCES).map(([key, text]) => [key.startsWith('./') ? `lib/${key.slice(2)}` : key.replace(/^\.\.\//, ''), text])
)

const ALLOWED = new Set(['lib/messages.ts', 'lib/messages.en.ts'])

// 中日韩文字与全角区段——真正的人类语言文字。间隔号之类的排版符号刻意不匹配。
const CJK = /[\u3000-\u303f\u3400-\u9fff\uf900-\ufaff\uff00-\uffef]/

function strayLiterals(text: string): number[] {
  const noBlock = text.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
  const hits: number[] = []
  noBlock.split('\n').forEach((line, index) => {
    const code = line.replace(/\/\/.*$/, '')
    for (const match of code.matchAll(/"([^"\\]*)"|'([^'\\]*)'|`([^`]*)`/g)) {
      const literal = match[1] ?? match[2] ?? match[3] ?? ''
      if (CJK.test(literal)) {
        hits.push(index + 1)
        break
      }
    }
  })
  return hits
}

describe('src/app 的文案收敛', () => {
  it('确实扫到了文件，词典也在（防止空扫描永远通过）', () => {
    expect(Object.keys(FILES).length).toBeGreaterThan(10)
    for (const allowed of ALLOWED) expect(Object.keys(FILES)).toContain(allowed)
    expect(Object.keys(FILES)).toContain('components/gate-frame.tsx')
    expect(strayLiterals("const a = '\u767b\u5f55'")).toEqual([1])
  })

  it('带中文的字符串字面量只在两份词典里', () => {
    const strays = Object.entries(FILES)
      .filter(([file]) => !ALLOWED.has(file))
      .map(([file, text]) => ({ file, lines: strayLiterals(text) }))
      .filter(({ lines }) => lines.length > 0)
      .map(({ file, lines }) => `${file} (lines ${lines.slice(0, 5).join(', ')})`)

    expect(strays, '把文字移进 messages.ts / messages.en.ts').toEqual([])
  })
})
