#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""仓内指引不得再教人写已退役的 Flyway 迁移。

2026-09-10 起数据库结构的唯一权威是 `deploy/database/ddl/`（基线 → `incr/` 编号增量 → 97 角色 → 98 列锁），
由 `db-init` 工作流施加，应用启动不做迁移（详细设计 §13b TD-001）。

**可是指引没跟上。** 2026-09-15 核对时仍有五处在教旧做法：`AGENTS.md`「迁移位于 resources/sql V1–V23，新增只能追加」、
两个仓内 AI 技能「新结构追加 Flyway 迁移、不改 V1–V22」、回滚工作流给运维看的「Flyway 迁移单向 V1–V30」、
详细设计修改定位表「表结构 → resources/sql 新 V23+」。照着做的人或 AI 会写出一个**没有任何东西施加**的
`V24__*.sql`：本地测试照样绿（测试施加的是 ddl 目录），生产上那张表永远不存在。

这类走样没有报错面，所以在这里给它一个：扫描指引类文件，出现退役迁移的**操作指引**就红。
说明「Flyway 已退役」的历史注释不含这些写法，照常通过。

反证做法（确认会红）：
  - 把「新数据库结构追加 Flyway 迁移，不修改 V1-V22。」放回 ai-code-engineering-standard 技能  → 红
  - 把「project-name-start/src/main/resources/sql/V1__*.sql」放回 AGENTS.md                    → 红
  - 把「Flyway 迁移单向」放回 rollback.yml                                                     → 红
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

#: 指引类文件：人和 AI 照着做事的地方。源码与历史注释不在这里。
GUIDANCE_GLOBS = [
    "AGENTS.md",
    "CLAUDE.md",
    ".claude/skills/**/*.md",
    "docs/30-design/*.md",
    "docs/50-deployment/**/*.md",
    ".github/workflows/*.yml",
]

#: 退役迁移的操作指引写法。每条都附上它会把人引向哪里。
RETIRED_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    (re.compile(r"\bV\d+__"), "Flyway 迁移文件名 V<n>__"),
    (re.compile(r"classpath:sql"), "Flyway 迁移位置 classpath:sql"),
    (re.compile(r"resources/sql"), "Flyway 迁移目录 resources/sql"),
    (re.compile(r"追加\s*(?:Flyway|V\d+)"), "「追加 Flyway / V<n>」"),
    (re.compile(r"新\s*V\d+\s*\+"), "「新 V<n>+」"),
    (re.compile(r"V1\s*[-–]\s*V\d+"), "「V1–V<n>」升级链"),
    (re.compile(r"Flyway\s*迁移(?:是)?单向"), "「Flyway 迁移单向」"),
]


def guidance_files() -> list[Path]:
    seen: set[Path] = set()
    for pattern in GUIDANCE_GLOBS:
        for path in ROOT.glob(pattern):
            if path.is_file():
                seen.add(path)
    return sorted(seen)


def main() -> int:
    problems: list[str] = []
    files = guidance_files()
    if not any(path.name == "AGENTS.md" for path in files):
        raise SystemExit("!! 找不到 AGENTS.md——这个守卫的扫描范围已经过期")
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            for pattern, meaning in RETIRED_PATTERNS:
                if pattern.search(line):
                    problems.append(f"{rel}:{number}: {meaning} —— {line.strip()[:120]}")
    if problems:
        print(f"!! 仓内指引仍在教已退役的 Flyway 迁移（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   结构变更只追加 deploy/database/ddl/incr/NNNN_*.sql（可重放），新列在 98_column_locks.sql 授权，")
        print("   由 db-init 工作流施加；应用启动不迁移。")
        return 1
    print(f"仓内指引未再出现退役的 Flyway 迁移写法（扫描 {len(files)} 个指引文件）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
