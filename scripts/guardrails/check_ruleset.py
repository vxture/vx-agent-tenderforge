#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""main 分支保护的 ruleset 不许被悄悄放松。

`doc/rebuild/main-ruleset.json` 是**逐字**应用到仓库上的那份配置。它被改弱
之后，GitHub 的设置页面上「保护」两个字还在，只是不再要求任何检查——
这正是最难发现的一类回退：没有报错，PR 照常能合，绿灯照常亮。

守四件事：

* 五个必需检查一个不少。作业名是稳定契约，改名会让保护静默失效。
* `bypass_actors` 为空。留一个绕过口子，保护就只对没有那个口子的人成立。
* `enforcement` 是 active，不是 evaluate（后者只报告、不阻断）。
* 删除与强推仍被禁止。

这份守卫也顺带核对 ruleset 里的检查名与工作流里真实存在的作业名对得上——
要求一个不存在的检查会让 PR 永远等一个不会到来的绿灯。
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RULESET = ROOT / "doc" / "rebuild" / "main-ruleset.json"
WORKFLOWS = ROOT / ".github" / "workflows"

REQUIRED_CHECKS = {"quality-gate", "build", "test-coverage", "audit", "gitleaks"}
REQUIRED_RULE_TYPES = {"deletion", "non_fast_forward", "required_status_checks"}


def workflow_job_names() -> set[str]:
    """工作流里 `name:` 声明的作业名。

    读的是 `name:`（GitHub 显示为检查名的那个）而不是 YAML 的 job key——
    两者可以不同，而分支保护要求的是前者。
    """
    names: set[str] = set()
    for path in sorted(WORKFLOWS.glob("*.yml")):
        text = path.read_text(encoding="utf-8")
        # 作业级的 `    name: x`（六格缩进以内、位于 jobs: 之下）。
        names.update(re.findall(r"^    name:\s*([A-Za-z0-9._-]+)\s*$", text, re.M))
    return names


def main() -> int:
    if not RULESET.exists():
        print(f"!! 找不到 {RULESET.relative_to(ROOT)}")
        return 1
    ruleset = json.loads(RULESET.read_text(encoding="utf-8"))

    problems: list[str] = []

    if ruleset.get("enforcement") != "active":
        problems.append(
            f"enforcement 是 {ruleset.get('enforcement')!r}，不是 active——"
            "evaluate 模式只报告不阻断，页面上看起来却是开着的"
        )

    bypass = ruleset.get("bypass_actors") or []
    if bypass:
        problems.append(
            f"bypass_actors 非空（{len(bypass)} 项）——留一个绕过口子，"
            "保护就只对没有那个口子的人成立"
        )

    rule_types = {rule.get("type") for rule in ruleset.get("rules", [])}
    missing_rules = REQUIRED_RULE_TYPES - rule_types
    if missing_rules:
        problems.append(f"缺少规则：{', '.join(sorted(missing_rules))}")

    declared_checks: set[str] = set()
    for rule in ruleset.get("rules", []):
        if rule.get("type") == "required_status_checks":
            declared_checks = {
                check["context"]
                for check in rule.get("parameters", {}).get("required_status_checks", [])
            }

    missing_checks = REQUIRED_CHECKS - declared_checks
    if missing_checks:
        problems.append(f"必需检查被摘掉：{', '.join(sorted(missing_checks))}")

    jobs = workflow_job_names()
    phantom = declared_checks - jobs
    if phantom:
        problems.append(
            f"ruleset 要求了工作流里不存在的检查：{', '.join(sorted(phantom))}"
            "——PR 会永远等一个不会到来的绿灯"
        )

    if problems:
        print("!! 分支保护配置有问题：")
        for problem in problems:
            print(f"   - {problem}")
        return 1

    print(
        f"分支保护完好：{len(declared_checks)} 个必需检查全部存在于工作流中，"
        "无绕过项，enforcement=active。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
