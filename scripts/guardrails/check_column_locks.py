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

抽取是**机械**的，但白名单不能直接用抽取结果——JdbcProvisioningRepository
用 `%s` 拼动态列名，机械抽取会得到一列叫 `s`。所以这里维护一张
`EXTRACTION_FIXUPS`：把已知的抽取偏差显式写下来，而不是放宽比对。
放宽比对会把真正的分叉一起放过去。
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

#: 机械抽取拿不准的地方，逐条写明。键是表名，值是该表真实的可写列集合。
EXTRACTION_FIXUPS: dict[str, set[str]] = {
    # JdbcProvisioningRepository 把时间戳列名拼成 `%s`（provisioned_at 与
    # deprovisioned_at 二选一），抽取器会得到一列叫 `s`。
    "platform_workspace_provision": {
        "state", "last_seq", "provisioned_at", "deprovisioned_at", "updated_at",
    },
}


def updates_in_code() -> dict[str, set[str]]:
    """从生产代码的 UPDATE 语句里抽出每张表被写的列。"""
    found: dict[str, set[str]] = defaultdict(set)
    pattern = re.compile(r"UPDATE\s+([a-z_]+)\s+SET\s+(.*?)(?:\s+WHERE|\"\s*[;+)])", re.I | re.S)
    for path in JAVA_MAIN.rglob("*.java"):
        if "src/test" in path.as_posix():
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        # Java 的字符串拼接压平，再按空白归一——SQL 常常跨多行拼出来。
        flat = re.sub(r"\s+", " ", re.sub(r'"\s*\+\s*"', " ", text))
        for match in pattern.finditer(flat):
            table = match.group(1).lower()
            for column in re.findall(r"([a-z_]+)\s*=", match.group(2)):
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
