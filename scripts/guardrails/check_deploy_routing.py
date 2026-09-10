#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
"""tag → 环境 的路由必须把每一类 tag 送到它该去的地方。

`deploy.yml` 的 `deploy` 作业按 `needs.detect.outputs.environment` 动态绑定环境，
所以那一段 `case` 决定的是**这次部署取哪一套 secret、落到哪台机器的哪个目录**。
它错一次的代价不是构建失败，是内容被发到了另一个环境，而日志里一切正常。

具体踩过的坑：glob 里 `v*.*.*` **同样匹配** `v0.1.0-beta.1`。把生产那条放在
前面，一个 beta tag 会被静默路由到生产。分支顺序因此是有载荷的。

**这个护栏跑的是 deploy.yml 里那一段本身**，不是抄一份出来测。抄出来的那份会
和真的分叉，而分叉之后护栏依然全绿——它测的是自己那份副本。所以这里把 case 块
从 YAML 里原样抽出来，喂给 bash 真跑。

它测不到的：case 块之外的东西（环境绑定写法、needs 依赖）。那部分由
`environment: name: ${{ needs.detect.outputs.environment }}` 这一行的存在性断言兜底。
"""

from __future__ import annotations

import re
import subprocess
import sys
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY = ROOT / ".github" / "workflows" / "deploy.yml"

#: (tag, 期望环境 或 None 表示必须被拒绝)
CASES = [
    ("v0.1.0", "production"),
    ("v1.2.3", "production"),
    ("v10.20.30", "production"),
    ("v0.1.0-beta.1", "beta"),
    ("v0.1.0-beta.12", "beta"),
    ("v2.0.0-beta.1", "beta"),
    # 没有对应环境的预发布形态：必须红，不能落到生产
    ("v0.1.0-rc.1", None),
    ("v0.1.0-alpha", None),
    ("v0.1.0-beta", None),
    # 根本不是发布 tag
    ("main", None),
    ("beta", None),
    ("release-0.1.0", None),
]


def case_block() -> str:
    text = DEPLOY.read_text(encoding="utf-8")
    match = re.search(r'^([ \t]*)case "\$TAG" in\n(.*?)^\1esac\s*$', text, re.M | re.S)
    if match is None:
        raise SystemExit("!! 在 deploy.yml 里找不到 `case \"$TAG\" in ... esac` 块")
    return textwrap.dedent(match.group(0))


def route(block: str, tag: str) -> tuple[int, str]:
    script = 'set -u\nenvironment=""\n' + block + '\nprintf "%s" "$environment"\n'
    done = subprocess.run(
        ["bash", "-c", script],
        env={"TAG": tag, "PATH": "/usr/bin:/bin"},
        capture_output=True,
        # **编码必须钉死**，不能跟随系统 locale：拒绝分支输出的是中文，而在
        # GBK 终端上 text=True 会让读取线程抛 UnicodeDecodeError。返回码碰巧
        # 仍然是对的，于是护栏「看起来通过」——靠运气成立的检查不算检查。
        encoding="utf-8",
        errors="replace",
    )
    return done.returncode, done.stdout.strip()


def main() -> int:
    if not DEPLOY.exists():
        print(f"!! 找不到 {DEPLOY.relative_to(ROOT)}")
        return 1

    block = case_block()
    problems: list[str] = []

    for tag, expected in CASES:
        code, got = route(block, tag)
        if expected is None:
            if code == 0:
                problems.append(f"{tag} 应当被拒绝，实际路由到 {got or '(空)'}")
        elif code != 0:
            problems.append(f"{tag} 应当路由到 {expected}，实际被拒绝")
        elif got != expected:
            problems.append(f"{tag} 应当路由到 {expected}，实际是 {got or '(空)'}")

    dynamic = "name: ${{ needs.detect.outputs.environment }}"
    if dynamic not in DEPLOY.read_text(encoding="utf-8"):
        problems.append(
            "deploy 作业没有按 detect 的输出动态绑定环境——路由算得再对也没有用，"
            "取到的仍是写死的那一套 secret"
        )

    if problems:
        print(f"!! tag 路由不正确（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   分支顺序是有载荷的：glob 里 v*.*.* 同样匹配 v0.1.0-beta.1，")
        print("   生产那条排在前面会把 beta tag 静默发到生产。")
        return 1

    print(f"tag 路由正确：{len(CASES)} 个样例全部落到预期环境（含 6 个必须被拒绝的形态）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
