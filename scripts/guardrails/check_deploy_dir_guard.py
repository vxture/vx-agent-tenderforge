#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-11
"""`assert-deploy-dir.sh` 必须真的拦得住那个错值。

这个断言存在的理由是一次真实事故：`DEPLOY_DIR` 被 Windows 上的 MSYS 路径转换
写成 `D:/Program Files/Git/srv/md0/tenderforge`，而它在 Linux 上是一个**合法的
相对路径**——`mkdir -p`、rsync、文件断言、远端第一次 `cd` 全部一致地成功，
在 stone 的家目录下建出一整棵没人认识的目录树。删掉它，下一次部署照样重建，
因为四个使用点里**一个校验点都没有**。

**本护栏跑的是那个脚本本身**，不是另抄一份规则来比对。抄出来的那份会和真的分叉，
而分叉之后护栏依然全绿——它测的是自己那份副本。

它还断言四个使用点都调了这个脚本：脚本再对，漏接一处就等于那一处没有保护，
而漏接不会有任何症状。
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "ci" / "assert-deploy-dir.sh"
WORKFLOWS = ROOT / ".github" / "workflows"

#: (值, 期望的拒绝理由；None 表示应当通过)
#:
#: **断言的是理由而不只是退出码。** 第一版只比对退出码，结果「去掉空值检查」
#: 那条反证没有变红——空串会被后面的「不是绝对路径」顺手拦下，于是那个检查
#: 在拦截上是冗余的，它的全部价值在于**报错说的是哪一件事**。
#: 只验退出码，等于允许一个检查被删掉而没有任何症状。
CASES = [
    ("/srv/md0/tenderforge", None),
    ("/srv/md1/tenderforge", None),
    ("/srv/md0/yucer", None),
    # 真实事故里的那个值
    ("D:/Program Files/Git/srv/md0/tenderforge", "冒号"),
    # 反斜杠用 chr(92) 拼，不写字面量：这个文件经手过的每一层
    # （heredoc、shell、Python 字面量）都会吃掉一次转义，而被吃掉之后
    # "C:	mp" 里的 	 会变成制表符——用例本身就不是原本要测的那个值了。
    ("C:" + chr(92) + "tmp" + chr(92) + "deploy", "反斜杠"),
    ("srv/md0/x", "不是绝对路径"),
    ("/srv/md0/x/", "斜杠结尾"),
    ("", "为空"),
    ("/srv/md0/a:b", "冒号"),
]


def run(value: str) -> tuple[int, str]:
    done = subprocess.run(
        ["bash", str(SCRIPT), value],
        capture_output=True,
        # 编码钉死：拒绝分支输出中文，跟随系统 locale 会在 GBK 终端上抛异常，
        # 而返回码碰巧仍是对的——靠运气成立的检查不算检查。
        encoding="utf-8",
        errors="replace",
    )
    return done.returncode, (done.stdout or "") + (done.stderr or "")


def call_sites() -> list[str]:
    """每个把 DEPLOY_DIR 当路径用的工作流，都必须调过这个脚本。"""
    missing = []
    for path in sorted(WORKFLOWS.glob("*.yml")):
        text = path.read_text(encoding="utf-8")
        if "deploy_dir=" not in text:
            continue
        uses = text.count("deploy_dir=")
        asserts = text.count("assert-deploy-dir.sh")
        if asserts < uses:
            missing.append(f"{path.name}: {uses} 处使用，只有 {asserts} 处断言")
    return missing


def main() -> int:
    if not SCRIPT.exists():
        print(f"!! 找不到 {SCRIPT.relative_to(ROOT)}")
        return 1

    problems = []
    for value, reason in CASES:
        code, output = run(value)
        if reason is None:
            if code != 0:
                problems.append(f"'{value}' 应当通过，实际被拒绝")
            continue
        if code == 0:
            problems.append(f"'{value}' 应当被拒绝，实际通过了")
        elif reason not in output:
            problems.append(
                f"'{value}' 被拒绝了，但理由里没有「{reason}」——"
                "报错说的不是同一件事，排查的人会被带去错的方向"
            )

    problems.extend(call_sites())

    if problems:
        print(f"!! DEPLOY_DIR 断言不成立（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   错值在 Linux 上是合法的相对路径，每一步都会一致地成功；")
        print("   这个断言是它唯一的报错面。")
        return 1

    print(
        f"DEPLOY_DIR 断言成立：{len(CASES)} 个样例判定正确"
        f"（含真实事故里的 Windows 路径），且所有使用点均已接入。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
