// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { inflateSync } from 'node:zlib'
import test from 'node:test'

// 门禁页规范的强制点（四张不渲染产品外壳的页面；参照实现 vx-agent-yucer，照搬）。
//
// 这些约束的共同点是走样不报错：宽度写成 max-w-xl 页面照样渲染、只是 32px；波浪少画几遍只是边缘有硬边；
// 退出按钮换成脚本点击平时一样能用；标识 PNG 底是白的在白底页面上看不出来。所以逐条钉在源码上。
// 取代原来的 login-page-standard.test.mjs——那张登录页已被引导页取代。

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const source = (path) => readFileSync(join(projectRoot, path), 'utf8')
const code = (path) =>
  source(path)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\{\/\*[\s\S]*?\*\/\}/g, '')
    .replace(/(^|[^:"'`])\/\/.*$/gm, '$1')

const walk = (dir, out = []) => {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) walk(path, out)
    else out.push(path)
  }
  return out
}

const frame = () => code('src/app/components/gate-frame.tsx')
const PAGES = ['sign-in', 'no-subscription', 'no-roles', 'signed-out']

test('four gate pages share one frame, one heading and one action block', () => {
  for (const page of PAGES) {
    const text = code(`src/app/components/${page}.tsx`)
    assert.match(text, /<GateFrame\b/, `${page} 没用共用框架`)
    assert.match(text, /<GateHeading\b/, `${page} 没用共用标题块`)
  }
  for (const page of ['no-subscription', 'no-roles', 'signed-out']) {
    assert.match(code(`src/app/components/${page}.tsx`), /<GateActions\b/, `${page} 没用共用动作块`)
  }
})

test('the header is measured from vxture.com and carries the three shell controls plus the website', () => {
  const text = frame()
  assert.match(text, /\bh-16\b/)
  assert.match(text, /max-w-\[1280px\] items-center justify-between xl:max-w-\[1536px\] 2xl:max-w-\[1600px\]/)
  assert.match(text, /px-md sm:px-lg lg:px-xl/)
  for (const control of ['ShellThemeToggle', 'ShellLocaleSwitcher', 'ShellFullscreenToggle']) {
    assert.match(text, new RegExp(`<${control}\\b`), `页头少了 ${control}`)
  }
  assert.match(text, /websiteUrl\(\)/)
})

test('three bands: identity and chain stay put, the middle sits above centre', () => {
  const text = frame()
  assert.match(text, /className="pt-6xl pb-3xl"/)
  assert.match(text, /pt-3xl pb-6xl/)
  assert.match(text, /\bpb-4xl\b/)
  // 下方留白是上方的两倍：两个撑杆，比例在任何视口都成立。
  assert.match(text, /<div className="flex-1" \/>[\s\S]*?<section[\s\S]*?<div className="flex-\[2\]" \/>/)
})

test('widths are explicit pixels, never the Tailwind names the design system redefines', () => {
  const text = frame()
  for (const width of ['max-w-[452px]', 'max-w-[760px]', 'max-w-[1040px]']) {
    assert.ok(text.includes(width), `缺少 ${width}`)
  }
  const files = walk(join(projectRoot, 'src/app')).filter((path) => /\.tsx?$/.test(path) && !/\.test\./.test(path))
  for (const file of files) {
    // DS 在这些名字上注册了自己的尺度：max-w-xl 解析出来是 32px。只看代码不看注释——注释里正写着这句警告。
    assert.doesNotMatch(
      code(relative(projectRoot, file)),
      /\bmax-w-(xs|sm|md|lg|xl|[2-7]xl)\b/,
      `${relative(projectRoot, file)} 用了被 DS 重定义的宽度档位`
    )
  }
})

test('the wave is one path drawn sixteen times, and every colour is a design token', () => {
  const text = frame()
  assert.match(text, /Array\.from\(\{ length: 16 \}/)
  assert.match(text, /y=\{i \* 14\}/)
  assert.match(text, /fillOpacity="0\.016"/)
  assert.match(text, /fill="var\(--primary\)"/)
  assert.match(text, /h-\[55%\]/)
  const files = walk(join(projectRoot, 'src/app')).filter((path) => /\.tsx?$/.test(path) && !/\.test\./.test(path))
  for (const file of files) {
    const body = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')
    assert.doesNotMatch(body, /#[0-9a-fA-F]{3,8}\b|\brgba?\(/, `${relative(projectRoot, file)} 在本地定义了色值`)
  }
})

test('actions: primary on top, outline secondaries of the same size, sign-out is a real form POST', () => {
  const text = code('src/app/components/gate-actions.tsx')
  assert.equal((text.match(/size="lg"/g) ?? []).length, 4, '主、重试、次、退出四种按钮同一个尺寸')
  assert.equal((text.match(/variant="outline"/g) ?? []).length, 2, '次按钮与退出同一个分量')
  assert.match(text, /<form method="post" action="\/api\/auth\/logout"/)
  assert.match(text, /<Button type="submit" variant="outline" size="lg"/)
  for (const page of PAGES) {
    assert.doesNotMatch(code(`src/app/components/${page}.tsx`), /<Button[^>]*variant=|logout\(|authApi/, `${page} 绕开了共用动作块`)
  }
})

test('the sign-in page offers one way in and no local password', () => {
  const page = code('src/app/components/sign-in.tsx')
  assert.match(page, /loginHref\(/)
  for (const forbidden of [/type=\{?["']?password/, /authApi\.login/, /remembered-login/, /storage\.set/]) {
    assert.doesNotMatch(page, forbidden)
  }
  assert.match(source('src/app/auth/return-to.ts'), /\/api\/auth\/oidc\/login\?returnTo=/)
})

test('image paths live in brand-assets.ts and nowhere else in the product', () => {
  const assets = source('src/app/lib/brand-assets.ts')
  for (const name of ['PRODUCT_MARK_SRC', 'BRAND_MARK_SRC', 'BRAND_WORDMARK']) {
    assert.match(assets, new RegExp(`export const ${name} =`))
  }
  const offenders = walk(join(projectRoot, 'src'))
    .filter((path) => /\.tsx?$/.test(path) && !/\.test\./.test(path) && !path.endsWith('brand-assets.ts'))
    .filter((path) => /['"`][^'"`]*\.(svg|png)['"`]/.test(readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')))
    .map((path) => relative(projectRoot, path))
  assert.deepEqual(offenders, [], '图片路径写在了 brand-assets.ts 之外')
})

test('the gate pages are wired: session-less visits render in place, the marker names match the server', () => {
  const guard = code('src/router/AuthGuard.tsx')
  assert.match(guard, /justSignedOut \? <SignedOut consoleHref=\{consoleUrl\(\)\} \/> : <SignIn \/>/)
  assert.doesNotMatch(guard, /\/login\?redirect=/)
  const marker = source('src/app/auth/signed-out-marker.ts').match(/SIGNED_OUT_COOKIE = '([^']+)'/)?.[1]
  const server = readFileSync(
    join(
      projectRoot,
      '../../backend/project-name-java/project-name-web/src/main/java/com/td/czghagent/rest/security/SignedOutMarker.java'
    ),
    'utf8'
  ).match(/NAME = "([^"]+)"/)?.[1]
  assert.ok(marker && server, '便条名没读出来，判据要重写')
  assert.equal(marker, server, '前端读的便条名与服务端种的不一致：已退出页永远不会出现')
  assert.match(code('src/app/components/signed-out.tsx'), /useEffect\(\(\) => \{\s*clearSignedOutMarker\(\)/)
  assert.match(source('src/main.tsx'), /document\.documentElement\.lang = resolveLocale\(\)/)
})

/** 沿设计系统 CSS 的 @import 链收集 token 定义（token 大多定义在被导入的包里）。 */
const readCssGraph = (file, seen = new Set()) => {
  if (seen.has(file)) return ''
  seen.add(file)
  const css = readFileSync(file, 'utf8')
  let out = css
  for (const [, spec] of css.matchAll(/@import\s+(?:url\()?["']([^"']+)["']/g)) {
    let next = null
    if (spec.startsWith('.')) next = resolve(dirname(file), spec)
    else {
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

test('every spacing and type token the frame spends is defined by the design system', () => {
  const entry = createRequire(join(projectRoot, 'package.json')).resolve('@vxture/design-system/styles/globals.css')
  const defined = new Set([...readCssGraph(entry).matchAll(/(--[a-zA-Z0-9-]+)\s*:/g)].map((m) => m[1]))
  assert.ok(defined.size > 100, `only ${defined.size} tokens found - the import graph was not followed`)

  const used = new Set()
  const classes = [...frame().matchAll(/className="([^"]+)"/g)].flatMap((m) => m[1].split(/\s+/))
  for (const cls of classes) {
    const bare = cls.replace(/^[a-z0-9]+:/, '')
    const spacing = bare.match(/^-?(?:p[trblxy]?|m[trblxy]?|gap(?:-[xy])?)-((?:\d)?(?:2xs|xs|sm|md|lg|xl|\dxl))$/)
    if (spacing) used.add(`--spacing-${spacing[1]}`)
    const type = bare.match(/^text-((?:heading|title|body|label)-[a-z0-9]+|overline)$/)
    if (type) used.add(`--text-${type[1]}`)
  }
  assert.ok(used.size >= 8, `the frame spends ${used.size} tokens - the class scan saw nothing`)
  assert.deepEqual([...used].filter((token) => !defined.has(token)), [], 'tokens the frame uses but the design system never defines')
})

/** 最小的 PNG 解码：只为核对尺寸、颜色类型与四角 alpha。不靠肉眼看。 */
function decodePng(buffer) {
  assert.equal(buffer.toString('ascii', 1, 4), 'PNG')
  const width = buffer.readUInt32BE(16)
  const height = buffer.readUInt32BE(20)
  const bitDepth = buffer[24]
  const colorType = buffer[25]
  const interlace = buffer[28]
  const chunks = []
  for (let offset = 8; offset < buffer.length; ) {
    const length = buffer.readUInt32BE(offset)
    const type = buffer.toString('ascii', offset + 4, offset + 8)
    if (type === 'IDAT') chunks.push(buffer.subarray(offset + 8, offset + 8 + length))
    offset += 12 + length
    if (type === 'IEND') break
  }
  const raw = inflateSync(Buffer.concat(chunks))
  const bpp = 4
  const stride = width * bpp
  const pixels = Buffer.alloc(height * stride)
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)]
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1))
    for (let x = 0; x < stride; x++) {
      const a = x >= bpp ? pixels[y * stride + x - bpp] : 0
      const b = y > 0 ? pixels[(y - 1) * stride + x] : 0
      const c = x >= bpp && y > 0 ? pixels[(y - 1) * stride + x - bpp] : 0
      let predictor = 0
      if (filter === 1) predictor = a
      else if (filter === 2) predictor = b
      else if (filter === 3) predictor = Math.floor((a + b) / 2)
      else if (filter === 4) {
        const p = a + b - c
        const pa = Math.abs(p - a)
        const pb = Math.abs(p - b)
        const pc = Math.abs(p - c)
        predictor = pa <= pb && pa <= pc ? a : pb <= pc ? b : c
      }
      pixels[y * stride + x] = (line[x] + predictor) & 0xff
    }
  }
  return { width, height, bitDepth, colorType, interlace, alpha: (x, y) => pixels[y * stride + x * bpp + 3] }
}

test('the product mark ships as SVG and as a 512px PNG whose corners are transparent', () => {
  assert.match(source('public/logo.svg'), /<svg[^>]+viewBox="0 0 256 256"/)
  const png = decodePng(readFileSync(join(projectRoot, 'public/logo.png')))
  assert.deepEqual([png.width, png.height, png.bitDepth, png.colorType, png.interlace], [512, 512, 8, 6, 0])
  const last = 511
  assert.deepEqual([png.alpha(0, 0), png.alpha(last, 0), png.alpha(0, last), png.alpha(last, last)], [0, 0, 0, 0])
  // 判据先验会不会动：标识中心必须是不透明的，否则四角为 0 什么也说明不了。
  assert.equal(png.alpha(256, 256), 255)
})
