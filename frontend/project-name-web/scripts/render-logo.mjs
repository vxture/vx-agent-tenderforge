// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-16
//
// 产品标识的唯一来源：同一套几何同时写出 public/logo.svg 与 public/logo.png（512，透明底）。
//
// 门禁页规范要求两种格式一起发布，且 PNG 由 SVG 的几何渲染、不是描出来的。所以几何只在这里定义一次：
// SVG 用一个矩阵把它放上 256 画布，PNG 用同一个矩阵把每个采样点反算回来判定覆盖——两边读的是同一组数。
// 改标识 = 改这里的常量，再跑一遍：
//
//   node scripts/render-logo.mjs
//
// 构图取自 owner 给定的参考图 Icons8「Pen」（Parakeet Partial Filled，见 docs/30-design/assets/brand/）：
// 斜放的钢笔笔尖——笔箍实心、笔身描边、中间一个气孔、气孔到笔尖一道缝。只依赖 node 自带模块。

import { writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { deflateSync } from 'node:zlib'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

const DARK = '#1E51FF'
const LIGHT = '#698CFF'

// 竖直摆放的笔尖（笔尖朝下），y 向下；数值与 256 画布同量级，最后整体缩放。
const COLLAR = { x: -44, y: -100, w: 88, h: 42, r: 10 } // 笔箍：实心浅色
const BODY = { top: -42, half: 32, tip: 108, stroke: 22 } // 笔身：深色描边
const HOLE_R = 14 // 气孔
const SLIT = { stroke: 10, end: 96 } // 缝：气孔下沿到笔尖前
const ANGLE = 45 // 顺时针旋转，笔尖朝左下
const EXTENT = 172 // 画布上的绘制范围（含笔宽），与 yucer 的 170 同一量级

const LEFT_CURVE = {
  p0: [-BODY.half, BODY.top],
  c1: [-BODY.half - 2, BODY.top + 60],
  c2: [-16, BODY.tip - 34],
  p3: [0, BODY.tip],
}

const round = (value, digits = 4) => Number(value.toFixed(digits))

function cubic({ p0, c1, c2, p3 }, steps = 96) {
  const points = []
  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    const u = 1 - t
    const at = (k) => u * u * u * p0[k] + 3 * u * u * t * c1[k] + 3 * u * t * t * c2[k] + t * t * t * p3[k]
    points.push([at(0), at(1)])
  }
  return points
}

// 笔身闭合轮廓：左半曲线下到笔尖，镜像回右上角，再由闭合段回到左上角。
const leftSide = cubic(LEFT_CURVE)
const outline = [...leftSide, ...leftSide.map(([x, y]) => [-x, y]).reverse().slice(1)]

const cos = Math.cos((ANGLE * Math.PI) / 180)
const sin = Math.sin((ANGLE * Math.PI) / 180)
const rotate = ([x, y]) => [x * cos - y * sin, x * sin + y * cos]

// ── 摆放：旋转后的包围盒居中于画布，气孔落在包围盒中心 ──────────────────────
//
// 竖直笔尖左右对称，转 45° 后关于那条斜轴对称，包围盒中心必然落在轴上；把气孔放在那里，
// 标识就「按构造」居中，而且画布正中那个像素是实心的（守卫测试逐像素核对它）。

function collarPoints() {
  const { x, y, w, h, r } = COLLAR
  const corners = [
    [x + w - r, y + r, -Math.PI / 2],
    [x + w - r, y + h - r, 0],
    [x + r, y + h - r, Math.PI / 2],
    [x + r, y + r, Math.PI],
  ]
  return corners.flatMap(([cx, cy, start]) =>
    Array.from({ length: 13 }, (_, i) => {
      const a = start + (i / 12) * (Math.PI / 2)
      return [cx + r * Math.cos(a), cy + r * Math.sin(a)]
    }),
  )
}

const extentSamples = [
  ...collarPoints().map((p) => [p, 0]),
  ...outline.map((p) => [p, BODY.stroke / 2]),
  [[0, SLIT.end], SLIT.stroke / 2],
]
let minX = Infinity
let minY = Infinity
let maxX = -Infinity
let maxY = -Infinity
for (const [point, radius] of extentSamples) {
  const [x, y] = rotate(point)
  minX = Math.min(minX, x - radius)
  maxX = Math.max(maxX, x + radius)
  minY = Math.min(minY, y - radius)
  maxY = Math.max(maxY, y + radius)
}
const centre = [(minX + maxX) / 2, (minY + maxY) / 2]
const holeOnAxis = [centre[0] * cos + centre[1] * sin, -centre[0] * sin + centre[1] * cos]
if (Math.abs(holeOnAxis[0]) > 0.5) throw new Error(`包围盒中心不在笔尖轴上：${holeOnAxis}`)
const HOLE_Y = round(holeOnAxis[1], 1)
if (HOLE_Y - HOLE_R < BODY.top + BODY.stroke / 2 || HOLE_Y + HOLE_R > SLIT.end) {
  throw new Error(`气孔 y=${HOLE_Y} 落在笔身之外`)
}

const scale = EXTENT / Math.max(maxX - minX, maxY - minY)
// SVG matrix(a b c d e f)：x' = a·x + c·y + e，y' = b·x + d·y + f。取整后的这组数同时给 PNG 用。
const rotatedHole = rotate([0, HOLE_Y])
const M = {
  a: round(scale * cos),
  b: round(scale * sin),
  c: round(-scale * sin),
  d: round(scale * cos),
  e: round(128 - scale * rotatedHole[0]),
  f: round(128 - scale * rotatedHole[1]),
}

// ── SVG ──────────────────────────────────────────────────────────────────────

const f = (n) => String(round(n, 2))
const { p0, c1, c2, p3 } = LEFT_CURVE
const mirror = ([x, y]) => [-x, y]
const bodyD =
  `M${f(p0[0])} ${f(p0[1])} ` +
  `C${f(c1[0])} ${f(c1[1])} ${f(c2[0])} ${f(c2[1])} ${f(p3[0])} ${f(p3[1])} ` +
  `C${mirror(c2).map(f).join(' ')} ${mirror(c1).map(f).join(' ')} ${mirror(p0).map(f).join(' ')} Z`

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256" role="img" aria-labelledby="tenderforge-logo-title">
  <title id="tenderforge-logo-title">TenderForge</title>
  <!--
    产品标识：一支钢笔的笔尖，斜放，笔尖朝左下。

    为什么是笔尖。这个产品做的事就是写标书（标书编写智能体），笔尖是「写」最直接的样子。
    构图取自 owner 给定的参考图 Icons8「Pen」（Parakeet Partial Filled），参考原图在
    docs/30-design/assets/brand/；这里是按那个构图重画的几何，不是描图。

    为什么两个色、各给谁。笔箍是大块实心，给浅色 #698CFF；笔身描边、气孔、缝是细线与小点，给深色
    #1E51FF。大面积能承载浅色，细线不能——yucer 的 logo.svg 记过同一个结论：浅色细线在页头 16px 下几乎看不见。
    配色沿用 Vxture 家族：#1E51FF 是品牌主色，#698CFF 是它与公司标识浅色 #B5C7FF 的中点。
    直接写十六进制而不是 currentColor：标识文件会被单独取走，背后没有样式表。

    几何。组内是竖直摆放的笔尖，一个矩阵把它转 45° 并放上画布：旋转后的包围盒居中，气孔正落在画布中心，
    所以居中是按构造的，而不是按眼睛。绘制范围（含笔宽）${Math.round(EXTENT)} 单位。

    不要手改这个文件：它与 logo.png 由 scripts/render-logo.mjs 从同一组数一起生成，改那里再跑一遍。
  -->
  <g transform="matrix(${M.a} ${M.b} ${M.c} ${M.d} ${M.e} ${M.f})">
    <rect x="${COLLAR.x}" y="${COLLAR.y}" width="${COLLAR.w}" height="${COLLAR.h}" rx="${COLLAR.r}" fill="${LIGHT}" />
    <path d="${bodyD}" fill="none" stroke="${DARK}" stroke-width="${BODY.stroke}" stroke-linejoin="round" />
    <path d="M0 ${HOLE_Y + HOLE_R} L0 ${SLIT.end}" fill="none" stroke="${DARK}" stroke-width="${SLIT.stroke}" stroke-linecap="round" />
    <circle cx="0" cy="${HOLE_Y}" r="${HOLE_R}" fill="${DARK}" />
  </g>
</svg>
`

// ── PNG：同一矩阵，逐采样点反算回竖直坐标判定覆盖 ─────────────────────────────

const SIZE = 512
const SUB = 4
const pixelScale = SIZE / 256
const det = M.a * M.d - M.b * M.c
const toUpright = (qx, qy) => {
  const x = qx - M.e
  const y = qy - M.f
  return [(M.d * x - M.c * y) / det, (-M.b * x + M.a * y) / det]
}

function segmentDistance(px, py, [ax, ay], [bx, by]) {
  const dx = bx - ax
  const dy = by - ay
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy || 1)))
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}

function insideCollar(x, y) {
  const { x: rx, y: ry, w, h, r } = COLLAR
  const qx = Math.abs(x - (rx + w / 2)) - (w / 2 - r)
  const qy = Math.abs(y - (ry + h / 2)) - (h / 2 - r)
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r <= 0
}

const bodyReach = BODY.stroke / 2
function onBody(x, y) {
  if (Math.abs(x) > BODY.half + bodyReach + 2 || y < BODY.top - bodyReach - 1 || y > BODY.tip + bodyReach + 1) return false
  for (let i = 0; i < outline.length; i++) {
    if (segmentDistance(x, y, outline[i], outline[(i + 1) % outline.length]) <= bodyReach) return true
  }
  return false
}

const onSlit = (x, y) => segmentDistance(x, y, [0, HOLE_Y + HOLE_R], [0, SLIT.end]) <= SLIT.stroke / 2
const inHole = (x, y) => Math.hypot(x, y - HOLE_Y) <= HOLE_R

const hex = (value) => [1, 3, 5].map((i) => parseInt(value.slice(i, i + 2), 16))
const darkRgb = hex(DARK)
const lightRgb = hex(LIGHT)

const stride = SIZE * 4 + 1
const raw = Buffer.alloc(SIZE * stride)
for (let py = 0; py < SIZE; py++) {
  raw[py * stride] = 0
  for (let px = 0; px < SIZE; px++) {
    let dark = 0
    let light = 0
    for (let sy = 0; sy < SUB; sy++) {
      for (let sx = 0; sx < SUB; sx++) {
        const [x, y] = toUpright((px + (sx + 0.5) / SUB) / pixelScale, (py + (sy + 0.5) / SUB) / pixelScale)
        // 叠放次序与 SVG 相同：笔箍在下，深色三件在上。
        if (inHole(x, y) || onSlit(x, y) || onBody(x, y)) dark++
        else if (insideCollar(x, y)) light++
      }
    }
    const covered = dark + light
    const offset = py * stride + 1 + px * 4
    if (covered === 0) continue
    for (let k = 0; k < 3; k++) raw[offset + k] = Math.round((dark * darkRgb[k] + light * lightRgb[k]) / covered)
    raw[offset + 3] = Math.round((covered / (SUB * SUB)) * 255)
  }
}

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})
function crc32(buffer) {
  let crc = 0xffffffff
  for (const byte of buffer) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const length = Buffer.alloc(4)
  length.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([length, body, crc])
}
const header = Buffer.alloc(13)
header.writeUInt32BE(SIZE, 0)
header.writeUInt32BE(SIZE, 4)
header.set([8, 6, 0, 0, 0], 8)
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', header),
  chunk('IDAT', deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0)),
])

const alphaAt = (x, y) => raw[y * stride + 1 + x * 4 + 3]
const last = SIZE - 1
if ([alphaAt(0, 0), alphaAt(last, 0), alphaAt(0, last), alphaAt(last, last)].some((a) => a !== 0)) {
  throw new Error('四角不透明：标识超出了画布')
}
if (alphaAt(SIZE / 2, SIZE / 2) !== 255) throw new Error('画布中心不是实心：气孔没有落在中心')

writeFileSync(join(root, 'public/logo.svg'), svg)
writeFileSync(join(root, 'public/logo.png'), png)
const toCanvas = (v, o) => round(128 + scale * (v - o), 1)
console.log(
  `logo.svg + logo.png written; extent x ${toCanvas(minX, rotatedHole[0])}..${toCanvas(maxX, rotatedHole[0])},` +
    ` y ${toCanvas(minY, rotatedHole[1])}..${toCanvas(maxY, rotatedHole[1])}; hole y=${HOLE_Y}; png ${png.length} bytes`,
)
