#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
"""巡检器与闸门必须扫同一个东西，且都不能冒充必需检查。

`ci.yml` 的 `audit` 是硬闸门（触发于改动），`sca-watch.yml` 是空闲主干的巡检器
（触发于定时）。两者查的是同一片依赖面，因此**必须用同一个扫描器、同一份钉住的
二进制、同一个扫描脚本**。它们漂开的三种后果，坏法各不相同：

* **巡检器的扫描器更旧** —— 它会在闸门要拦的发现上报「干净」。这是最坏的一种：
  绿色是假的，而假绿色比没有巡检更糟，因为它让人停止怀疑。
* **巡检器的扫描器更新** —— 主干上会出现闸门放行、巡检报警的条目，看起来像
  巡检器坏了，于是被当成噪音关掉。
* **扫描脚本不同** —— 两边覆盖的生态不一样，而这件事从任何一边的输出里都看不出来。

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
CI = ROOT / ".github" / "workflows" / "ci.yml"
WATCH = ROOT / ".github" / "workflows" / "sca-watch.yml"

#: 分支保护要求的五个上下文。别的工作流不得产生同名作业。
RESERVED_CONTEXTS = {"quality-gate", "build", "test-coverage", "audit", "gitleaks"}

#: 必须在两个文件里逐字相同的钉值。
PINNED = ("OSV_SCANNER_VERSION", "CYCLONEDX_PLUGIN_VERSION")


def pinned_value(text: str, key: str) -> str | None:
    match = re.search(rf'^\s*{key}:\s*"?([^"\s#]+)"?', text, re.M)
    return match.group(1) if match else None


def pinned_sha(text: str) -> str | None:
    match = re.search(r"^\s*sha256=([0-9a-f]{64})\s*$", text, re.M)
    return match.group(1) if match else None


def job_names(text: str) -> set[str]:
    """`name:` 决定检查上下文的显示名，作业 key 只在没有 name 时兜底。"""
    names: set[str] = set()
    in_jobs = False
    job_key: str | None = None
    for line in text.splitlines():
        if re.match(r"^jobs:\s*$", line):
            in_jobs = True
            continue
        if in_jobs and re.match(r"^\S", line):
            in_jobs = False
        if not in_jobs:
            continue
        key = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if key:
            job_key = key.group(1)
            names.add(job_key)
            continue
        named = re.match(r"^    name:\s*(.+?)\s*$", line)
        if named and job_key:
            names.discard(job_key)
            names.add(named.group(1).strip("\"'"))
    return names


def main() -> int:
    problems: list[str] = []

    for path in (CI, WATCH):
        if not path.exists():
            print(f"!! 找不到 {path.relative_to(ROOT)}")
            return 1

    ci_text = CI.read_text(encoding="utf-8")
    watch_text = WATCH.read_text(encoding="utf-8")

    for key in PINNED:
        a, b = pinned_value(ci_text, key), pinned_value(watch_text, key)
        if a is None or b is None:
            problems.append(f"{key} 在 {'ci.yml' if a is None else 'sca-watch.yml'} 里找不到")
        elif a != b:
            problems.append(f"{key} 漂开了：ci.yml={a}，sca-watch.yml={b}")

    sha_ci, sha_watch = pinned_sha(ci_text), pinned_sha(watch_text)
    if sha_ci is None or sha_watch is None:
        problems.append("下载步骤里的 sha256 钉值找不到——扫描器二进制没有被校验")
    elif sha_ci != sha_watch:
        problems.append(f"osv-scanner 的 sha256 漂开了：{sha_ci[:12]}… vs {sha_watch[:12]}…")

    script = "scripts/ci/sca-scan.sh"
    for label, text in (("ci.yml", ci_text), ("sca-watch.yml", watch_text)):
        if script not in text:
            problems.append(f"{label} 没有调用 {script}——两边覆盖的生态可能已经不同")

    collisions = job_names(watch_text) & RESERVED_CONTEXTS
    if collisions:
        problems.append(
            f"sca-watch.yml 产生了保留的检查上下文：{', '.join(sorted(collisions))}。"
            "规则集将无法分辨哪个才是它要求的那个，而分支保护看起来仍然生效"
        )

    codeql = ROOT / ".github" / "workflows" / "codeql.yml"
    if codeql.exists():
        codeql_collisions = job_names(codeql.read_text(encoding="utf-8")) & RESERVED_CONTEXTS
        if codeql_collisions:
            problems.append(
                f"codeql.yml 产生了保留的检查上下文：{', '.join(sorted(codeql_collisions))}"
            )

    if problems:
        print(f"!! SCA 锁步检查失败（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   闸门与巡检器必须扫同一个东西；巡检器用更旧的扫描器会报出假的干净。")
        return 1

    print(
        f"SCA 锁步一致：osv-scanner {pinned_value(ci_text, 'OSV_SCANNER_VERSION')}"
        f"（sha {sha_ci[:12]}…）、cyclonedx {pinned_value(ci_text, 'CYCLONEDX_PLUGIN_VERSION')}，"
        f"两边同调 {script}，且未占用五个保留上下文。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
