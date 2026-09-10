#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
"""巡检器与闸门必须扫同一个东西，且都不能冒充必需检查。

`ci.yml` 的 `audit` 是硬闸门（触发于改动），`sca-watch.yml` 是空闲主干的巡检器
（触发于定时）。两者查的是同一片依赖面，因此**必须用同一个扫描器、同一份钉住的
二进制、同一个依赖解析器、同一个扫描脚本**。它们漂开的后果，坏法各不相同：

* **巡检器的扫描器更旧** —— 它会在闸门要拦的发现上报「干净」。这是最坏的一种：
  绿色是假的，而假绿色比没有巡检更糟，因为它让人停止怀疑。
* **巡检器的扫描器更新** —— 主干上会出现闸门放行、巡检报警的条目，看起来像
  巡检器坏了，于是被当成噪音关掉。
* **依赖解析器（uv / cyclonedx）版本不同** —— 两边算出来的依赖树可能不是同一棵，
  而这个差异不会体现在任何一份报告里：两边各自看都合理。
* **扫描脚本不同** —— 两边覆盖的生态不一样，同样从任何一边的输出里都看不出来。

另一条断言是**检查上下文不得撞名**。`quality-gate` / `build` / `test-coverage` /
`audit` / `gitleaks` 这五个名字是分支保护契约的一部分；任何工作流再产生一个同名
上下文，规则集就无法分辨哪个才是它要求的那个——而表现是分支保护看起来仍然生效。

**这个护栏是必要而不充分的。** 它比对的是文件里的字面量，管不了「钉的 sha256 是否
真的对应那个版本」——那件事由下载步骤里的 `sha256sum -c` 在运行时保证。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
CI = WORKFLOWS / "ci.yml"
WATCH = WORKFLOWS / "sca-watch.yml"
CODEQL = WORKFLOWS / "codeql.yml"

#: 分支保护要求的五个上下文。别的工作流不得产生同名作业。
RESERVED_CONTEXTS = {"quality-gate", "build", "test-coverage", "audit", "gitleaks"}

#: 必须在两个文件里逐字相同的钉值。
PINNED = ("OSV_SCANNER_VERSION", "CYCLONEDX_PLUGIN_VERSION", "UV_VERSION")

#: 两边都必须调用的扫描脚本——它决定覆盖哪几个生态。
SCAN_SCRIPT = "scripts/ci/sca-scan.sh"

_JOB_KEY = re.compile(r"^ {2}([A-Za-z0-9_-]+):[ 	]*$")
_JOB_NAME = re.compile(r"^ {4}name:[ 	]*(.*)$")
_SHA = re.compile(r"^[ 	]*sha256=([0-9a-f]{64})[ 	]*$", re.M)


def pinned_value(text: str, key: str) -> str | None:
    match = re.search(rf'^[ 	]*{key}:[ 	]*"?([^"\s#]+)"?', text, re.M)
    return match.group(1) if match else None


def pinned_sha(text: str) -> str | None:
    match = _SHA.search(text)
    return match.group(1) if match else None


def job_names(text: str) -> set[str]:
    """`name:` 决定检查上下文的显示名，作业 key 只在没有 name 时兜底。"""
    names: set[str] = set()
    in_jobs = False
    job_key: str | None = None
    for line in text.splitlines():
        if line.rstrip() == "jobs:":
            in_jobs = True
            continue
        if in_jobs and line[:1].strip():
            in_jobs = False
        if not in_jobs:
            continue
        key = _JOB_KEY.match(line)
        if key:
            job_key = key.group(1)
            names.add(job_key)
            continue
        named = _JOB_NAME.match(line)
        if named and job_key:
            names.discard(job_key)
            names.add(named.group(1).strip().strip("\"'"))
    return names


def pin_problems(ci_text: str, watch_text: str) -> list[str]:
    """闸门与巡检器的钉值必须逐字相同。"""
    problems: list[str] = []
    for key in PINNED:
        gate, watcher = pinned_value(ci_text, key), pinned_value(watch_text, key)
        if gate is None:
            problems.append(f"{key} 在 ci.yml 里找不到")
        elif watcher is None:
            problems.append(f"{key} 在 sca-watch.yml 里找不到")
        elif gate != watcher:
            problems.append(f"{key} 漂开了：ci.yml={gate}，sca-watch.yml={watcher}")

    sha_gate, sha_watch = pinned_sha(ci_text), pinned_sha(watch_text)
    if sha_gate is None or sha_watch is None:
        problems.append("下载步骤里的 sha256 钉值找不到——扫描器二进制没有被校验")
    elif sha_gate != sha_watch:
        problems.append(f"osv-scanner 的 sha256 漂开了：{sha_gate[:12]}… vs {sha_watch[:12]}…")

    for label, text in (("ci.yml", ci_text), ("sca-watch.yml", watch_text)):
        if SCAN_SCRIPT not in text:
            problems.append(f"{label} 没有调用 {SCAN_SCRIPT}——两边覆盖的生态可能已经不同")

    return problems


def context_problems() -> list[str]:
    """除 ci.yml 外的工作流都不得产生那五个保留上下文。"""
    problems: list[str] = []
    for path in (WATCH, CODEQL):
        if not path.exists():
            continue
        collisions = job_names(path.read_text(encoding="utf-8")) & RESERVED_CONTEXTS
        if collisions:
            problems.append(
                f"{path.name} 产生了保留的检查上下文：{', '.join(sorted(collisions))}。"
                "规则集将无法分辨哪个才是它要求的那个，而分支保护看起来仍然生效"
            )
    return problems


def main() -> int:
    for path in (CI, WATCH):
        if not path.exists():
            print(f"!! 找不到 {path.relative_to(ROOT)}")
            return 1

    ci_text = CI.read_text(encoding="utf-8")
    watch_text = WATCH.read_text(encoding="utf-8")

    problems = pin_problems(ci_text, watch_text) + context_problems()
    if problems:
        print(f"!! SCA 锁步检查失败（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   闸门与巡检器必须扫同一个东西；巡检器用更旧的扫描器会报出假的干净。")
        return 1

    pins = "、".join(f"{key.split('_')[0].lower()} {pinned_value(ci_text, key)}" for key in PINNED)
    print(
        f"SCA 锁步一致：{pins}（osv sha {pinned_sha(ci_text)[:12]}…），"
        f"两边同调 {SCAN_SCRIPT}，且未占用五个保留上下文。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
