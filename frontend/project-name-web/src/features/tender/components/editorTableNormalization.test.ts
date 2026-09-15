// @vitest-environment happy-dom
// GENERATED_BY_AI
// MODEL: claude-opus-5
// DATE: 2026-09-15
import { describe, expect, it } from 'vitest'

import { normalizeEditorTables, numberEditorTables } from './editorTableNormalization'

const mount = (html: string) => {
  const container = document.createElement('div')
  container.innerHTML = html
  return container
}

const titles = (container: HTMLElement) =>
  Array.from(container.querySelectorAll('p[data-table-title]')).map((p) => p.textContent)

describe('normalizeEditorTables', () => {
  it('没有表题的表补一个由表头生成的兜底表题，并在表后补一个空表注', () => {
    const container = mount(
      '<table><tr><th>指标</th><th>要求</th></tr><tr><td>响应时间</td><td>≤2 秒</td></tr></table>'
    )

    normalizeEditorTables(container)

    const table = container.querySelector('table')!
    expect(table.previousElementSibling?.getAttribute('data-table-title')).toBe('true')
    expect(titles(container)).toEqual(['指标与要求对照表'])
    expect(table.nextElementSibling?.getAttribute('data-table-note')).toBe('true')
  })

  it('给了章节上下文时兜底表题用章节名，同一章第二张起带序号', () => {
    const row = '<tr><td>a</td><td>b</td></tr>'
    const container = mount(`<table>${row}${row}</table><table>${row}${row}</table>`)

    normalizeEditorTables(container, '验收方法')

    expect(titles(container)).toEqual(['验收方法明细表', '验收方法明细表（2）'])
  })

  it('<caption> 转成表题段落，caption 本身移除', () => {
    const container = mount(
      '<table><caption>表1 资源配置</caption><tr><td>岗位</td><td>人数</td></tr><tr><td>项目经理</td><td>1</td></tr></table>'
    )

    normalizeEditorTables(container)

    expect(titles(container)).toEqual(['表1 资源配置'])
    expect(container.querySelector('caption')).toBeNull()
  })

  it('表前重复的表题段落只保留一个，并去掉「表题：」前缀', () => {
    const container = mount(
      '<p>表题：人员配置表</p><p>表2 人员配置表</p><table><tr><td>岗位</td><td>人数</td></tr><tr><td>工程师</td><td>3</td></tr></table>'
    )

    normalizeEditorTables(container)

    expect(titles(container)).toEqual(['人员配置表'])
    expect(container.querySelectorAll('p').length).toBe(2)
  })

  it('第一行是跨列的表题单元格时挪成表题段落，表格只剩数据行', () => {
    const container = mount(
      '<table><tr><td colspan="2">设备清单</td></tr><tr><td>名称</td><td>数量</td></tr><tr><td>服务器</td><td>2</td></tr></table>'
    )

    normalizeEditorTables(container)

    expect(titles(container)).toEqual(['设备清单'])
    expect(container.querySelector('table')!.rows.length).toBe(2)
  })

  it('表后已有「注：」段落时只打标记，不再补第二个表注', () => {
    const container = mount(
      '<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table><p>注：数据来自招标文件</p>'
    )

    normalizeEditorTables(container)

    const notes = container.querySelectorAll('p[data-table-note]')
    expect(notes.length).toBe(1)
    expect(notes[0].textContent).toBe('注：数据来自招标文件')
  })
})

describe('numberEditorTables', () => {
  it('按章节号与起始序号给每张表编号，替换掉旧编号而不是叠加', () => {
    const container = mount(
      '<p>表 1-1 设备清单</p><table><tr><td>名称</td><td>数量</td></tr><tr><td>服务器</td><td>2</td></tr></table>' +
        '<table><tr><th>指标</th><th>要求</th></tr><tr><td>响应</td><td>2 秒</td></tr></table>'
    )
    normalizeEditorTables(container)

    numberEditorTables(container, { chapterNumber: 2, tableStart: 4, fallbackTitle: '验收方法' })

    expect(titles(container)).toEqual(['表 2-4 设备清单', '表 2-5 指标与要求对照表'])
  })
})
