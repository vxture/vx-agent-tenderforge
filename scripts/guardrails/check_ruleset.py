#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""main 分支保护的 ruleset 不许被悄悄放松。

`docs/50-deployment/rebuild/main-ruleset.json` 是**逐字**应用到仓库上的那份配置。它被改弱
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
RULESET = ROOT / "docs" / "50-deployment" / "rebuild" / "main-ruleset.json"
TAG_RULESET = ROOT / "docs" / "50-deployment" / "rebuild" / "tag-ruleset.json"
WORKFLOWS = ROOT / ".github" / "workflows"

REQUIRED_CHECKS = {"quality-gate", "build", "test-coverage", "audit", "gitleaks"}
REQUIRED_RULE_TYPES = {"deletion", "non_fast_forward", "required_status_checks"}

#: tag 规则集的必需规则。`creation` 是其中最要紧的一条：CD 由 `v*.*.*` 的推送触发，
#: 所以「谁能建这个 tag」就是整条发布链的信任根。不限制它，分支保护做得再严也只是
#: 拦住了一条路——另一条路直通生产。
TAG_REQUIRED_RULE_TYPES = {"creation", "update", "deletion", "non_fast_forward"}

#: 合并 main 需要的最少审批数。0 意味着「强制走 PR」只是形式：作者自己就能合。
MIN_APPROVALS = 1


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


def tag_ruleset_problems() -> list[str]:
    """tag 规则集：CD 的信任根。

    分支保护管的是「代码怎么进 main」，tag 规则集管的是「谁能把 main 上的某个提交
    变成一次生产发布」。只做前者不做后者，等于门锁了、窗开着——`deploy.yml` 触发于
    `v*.*.*`，任何能推 tag 的人都能直接发车。
    """
    if not TAG_RULESET.exists():
        return [
            f"找不到 {TAG_RULESET.relative_to(ROOT)}——tag 推送没有任何限制，"
            "而推 tag 就是触发生产部署"
        ]

    problems: list[str] = []
    ruleset = json.loads(TAG_RULESET.read_text(encoding="utf-8"))

    if ruleset.get("target") != "tag":
        problems.append(f"tag-ruleset.json 的 target 是 {ruleset.get('target')!r}，不是 tag")
    if ruleset.get("enforcement") != "active":
        problems.append(f"tag 规则集的 enforcement 是 {ruleset.get('enforcement')!r}，不是 active")

    includes = ruleset.get("conditions", {}).get("ref_name", {}).get("include", [])
    if "refs/tags/v*" not in includes:
        problems.append(
            f"tag 规则集没有覆盖 refs/tags/v*（当前 {includes}）——"
            "deploy.yml 正是由这个形状的 tag 触发的"
        )

    missing = TAG_REQUIRED_RULE_TYPES - {rule.get("type") for rule in ruleset.get("rules", [])}
    if missing:
        problems.append(f"tag 规则集缺少规则：{', '.join(sorted(missing))}")

    actors = {actor.get("actor_type") for actor in ruleset.get("bypass_actors") or []}
    if not actors:
        problems.append(
            "tag 规则集的 bypass_actors 为空——连组织管理员都建不了发布 tag，CD 无法发车。"
            "这里与分支规则集不同：分支那边空名单是对的，tag 这边需要恰好一个能发布的角色"
        )
    elif actors - {"OrganizationAdmin"}:
        problems.append(
            f"tag 规则集的绕过角色超出组织管理员：{', '.join(sorted(actors))}——"
            "能建发布 tag 的人就是能发生产的人"
        )

    return problems


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

    for rule in ruleset.get("rules", []):
        if rule.get("type") == "pull_request":
            approvals = rule.get("parameters", {}).get("required_approving_review_count", 0)
            if approvals < MIN_APPROVALS:
                problems.append(
                    f"required_approving_review_count 是 {approvals}——"
                    "强制走 PR 却零审批即可合并，作者自己就能合掉自己的改动"
                )

    problems.extend(tag_ruleset_problems())

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
        f"无绕过项，enforcement=active，合并需 {MIN_APPROVALS} 个审批；"
        "tag 规则集覆盖 refs/tags/v*，仅组织管理员可建发布 tag。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
