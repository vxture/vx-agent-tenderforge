#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
"""列级 UPDATE 白名单必须与代码里真实的 UPDATE 语句一致。

`deploy/database/ddl/98_column_locks.sql` 决定运行时角色能写哪些列。它与代码
分叉的两个方向，坏法完全不同：

* **代码写了、白名单没给** → 生产上 `permission denied for table ...`。
  本地开发多半以 owner 连库，所以这个错**只在生产出现**，而错误信息里
  不会提到是哪一列。
* **白名单给了、代码不写** → 没有报错，只是权限比需要的大。一次注入的
  爆炸半径从「白名单那几列」变回「整表」，而这件事没有任何信号。

所以两个方向都要红。

**这个护栏是必要而不充分的。** 列名由代码运行时拼出来时（本仓有两处：
`%s` 格式化，以及按对象类型拼 `overview_*` / `scoring_*`），任何基于源码文本的
提取都看不见它。那些情形逐条记在 `EXTRACTION_FIXUPS` 里——**显式写下来，
而不是放宽比对**，放宽会把真正的分叉一起放过去。

充分的那个检查是集成测试：它们以受限角色 `tenderforge_svc` 连库
（见 `PostgresBackedTest`），少给一列就会红。这不是冗余——护栏用同一个提取器
算期望值，**它结构上抓不到自己的盲点**，而两处 fixup 都是测试先红才补上的。
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_MAIN = ROOT / "backend" / "project-name-java"
LOCKS = ROOT / "deploy" / "database" / "ddl" / "98_column_locks.sql"

#: 锚点列永不可写（治理规范 §7），代码里也不会更新它们。
ANCHOR = {"id", "created_at", "event_id", "delivery_id", "workspace_id", "product"}

#: **静态提取看不见的地方**，逐条写明真实的可写列集合。
#:
#: 这不是"容错"，是这个护栏能力的边界：列名由代码在运行时拼出来时，任何基于
#: 源码文本的提取都看不见它。所以这个护栏是**必要而不充分**的——充分的那个
#: 检查是以受限角色跑的集成测试（PostgresBackedTest），下面两条都是它先红、
#: 才回来补进这张表的。
EXTRACTION_FIXUPS: dict[str, set[str]] = {
    # JdbcProvisioningRepository 用 %s 拼时间戳列名（provisioned_at 与
    # deprovisioned_at 二选一），静态抽取会得到一列叫 `s`。
    "platform_workspace_provision": {
        "state", "last_seq", "provisioned_at", "deprovisioned_at", "updated_at",
    },
    # JdbcBidDraftPersistence.objectColumn(objectType, suffix) 按解读对象类型
    # 拼出 overview_* / scoring_* 两套列名，源码里根本不存在
    # `overview_content = ?` 这样的字面量。集成测试报 permission denied
    # 才发现——静态提取与白名单共用同一个提取器，它抓不到自己这类盲点。
    "bid_source_file": {
        "parse_status", "parse_stage", "parse_progress", "parse_started_at",
        "parse_finished_at", "extracted_text", "error_message", "updated_at",
        "overview_status", "overview_content", "overview_error_message",
        "overview_completed_at",
        "scoring_status", "scoring_content", "scoring_error_message",
        "scoring_completed_at",
    },
}


def _sql_fragments(text: str) -> list[str]:
    """把一个 Java 文件切成互不相连的 SQL 片段。

    **不能整文件压平**。第一版就是那么做的，结果两条相邻语句被连成一条：
    一个 UPDATE 的 SET 子句一路吃到下一条语句的 WHERE，于是既凭空多出别的表
    的列（假阳性），又漏掉本该有的列（假阴性）。而护栏用同一个提取器算期望值，
    **它永远抓不到自己这类错**——真正抓到的是以受限角色跑的集成测试。

    所以按**文本块与字符串字面量的边界**切：每个 Java 文本块、每个双引号
    字面量各成一段，段内才做空白归一。

    （这段说明本身也踩过一次：原文里写了三引号的字面样子，把 docstring
    自己终止掉了。注释里出现语言自己的定界符，是个能让文件语法错的坑。）
    """
    fragments: list[str] = []
    for block in re.findall(r'"""(.*?)"""', text, re.S):
        fragments.append(re.sub(r"\s+", " ", block))
    # 去掉文本块之后剩下的普通字面量（可能跨行用 + 拼接）
    rest = re.sub(r'""".*?"""', " ", text, flags=re.S)
    rest = re.sub(r'"\s*\+\s*"', "", rest)
    for literal in re.findall(r'"([^"\n]*)"', rest):
        fragments.append(re.sub(r"\s+", " ", literal))
    return fragments


def _set_clause(fragment: str, start: int) -> str:
    """从 SET 之后截到**括号深度为 0 的那个 WHERE**。

    不能简单地停在第一个 WHERE：SET 里可以有带自己 WHERE 的子查询，例如
    `completed_units = (SELECT COUNT(*) FROM ... WHERE task_id = ?)`。
    停在那个 WHERE 上，后面的列（`retry_count` 就是）全部丢掉，而丢掉的
    表现是生产上 permission denied——静态检查这边一片绿。
    """
    depth = 0
    i = start
    while i < len(fragment):
        char = fragment[i]
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        elif depth == 0 and fragment[i:i + 7].upper() == " WHERE ":
            return fragment[start:i]
        i += 1
    return fragment[start:]


def updates_in_code() -> dict[str, set[str]]:
    """从生产代码的 UPDATE 语句里抽出每张表被写的列。"""
    found: dict[str, set[str]] = defaultdict(set)
    pattern = re.compile(r"UPDATE\s+([a-z_]+)\s+SET\s+", re.I)
    for path in JAVA_MAIN.rglob("*.java"):
        if "src/test" in path.as_posix():
            continue
        for fragment in _sql_fragments(path.read_text(encoding="utf-8", errors="ignore")):
            for match in pattern.finditer(fragment):
                table = match.group(1).lower()
                clause = _set_clause(fragment, match.end())
                # 只取深度 0 的赋值：子查询里的 `WHERE task_id = ?` 不是被写的列。
                depth, buffer = 0, []
                for char in clause:
                    if char == "(":
                        depth += 1
                    elif char == ")":
                        depth -= 1
                    elif depth == 0:
                        buffer.append(char)
                for column in re.findall(r"([a-z_]+)\s*=", "".join(buffer)):
                    if column not in {"and", "or", "where"}:
                        found[table].add(column)
    for table, columns in EXTRACTION_FIXUPS.items():
        if table in found:
            found[table] = set(columns)
    return {t: {c for c in cols if c not in ANCHOR} for t, cols in found.items()}


def grants_in_whitelist() -> dict[str, set[str]]:
    """98_column_locks.sql 里 GRANT UPDATE (...) 授出的列。"""
    text = LOCKS.read_text(encoding="utf-8")
    granted: dict[str, set[str]] = {}
    for match in re.finditer(
        r"GRANT UPDATE \(([^)]*)\)\s*ON\s+\w+\.(\w+)\s+TO", text, re.S
    ):
        columns = {c.strip() for c in match.group(1).split(",") if c.strip()}
        granted[match.group(2)] = columns
    return granted


def report(label: str, rows: list[str], hint: str) -> bool:
    if not rows:
        return True
    print(f"!! {label}（{len(rows)} 处）")
    for row in rows:
        print(f"     {row}")
    print(f"   {hint}")
    return False


def main() -> int:
    if not LOCKS.exists():
        print(f"!! 找不到 {LOCKS.relative_to(ROOT)}")
        return 1

    code = updates_in_code()
    granted = grants_in_whitelist()

    ungranted, unused = [], []
    for table, columns in sorted(code.items()):
        missing = columns - granted.get(table, set())
        if missing:
            ungranted.append(f"{table}: {', '.join(sorted(missing))}")
    for table, columns in sorted(granted.items()):
        surplus = columns - code.get(table, set())
        if surplus:
            unused.append(f"{table}: {', '.join(sorted(surplus))}")

    ok = True
    ok &= report(
        "代码更新了这些列，而白名单没给",
        ungranted,
        "生产上会是 permission denied，而本地以 owner 连库时完全不显形。"
        "补进 98_column_locks.sql 的 GRANT UPDATE。",
    )
    ok &= report(
        "白名单给了这些列，而代码并不更新它们",
        unused,
        "没有报错，只是权限比需要的大——一次注入的爆炸半径从几列变回整表。"
        "从 98_column_locks.sql 里删掉，或说明为什么留着。",
    )

    if ok:
        writable = sum(len(v) for v in granted.values())
        print(
            f"列锁一致：{len(granted)} 张表授出 {writable} 个可写列，"
            f"与代码里的 UPDATE 逐列对齐。"
        )
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
