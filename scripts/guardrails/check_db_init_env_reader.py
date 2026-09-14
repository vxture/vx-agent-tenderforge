#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""db-init 读宿主机 .env 的方式：只按键取值，**不执行文件内容**。

真实事故（2026-09-15，生产）：db-init 的远端脚本 `set -a; . ./.env`，而 compose 的
.env 允许未加引号的空格值。`OIDC_SCOPES=openid profile email phone` 被 bash 拆成
「临时赋值 + 执行 profile 命令」，db-init 以 127 退出，一条 DDL 都没施加。
compose 读同一行完全正常，所以服务一直是好的——两种解析器看同一个文件，
只有 shell 那一个会把配置当代码跑。

本护栏做两件事：

1. **跑 deploy/database/env-value.sh 本身**，喂一份带空格、引号、命令替换、分号、
   反引号、CRLF 与重复键的 .env，断言值取对了，并且文件里的命令一条都没有被执行
   （用标记文件判——「值取对了」不能证明「没执行」，一个先 source 再读的实现两者都会满足）。
2. **静态断言 db-init.yml 的远端脚本**：不再 source .env；脚本里用到的每个来自
   .env 的变量都经 env_value 读取。新引用一个变量却忘了读，会在这里红，
   而不是在生产上以「unbound variable」退出。
"""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
READER = ROOT / "deploy" / "database" / "env-value.sh"
DB_INIT = ROOT / ".github" / "workflows" / "db-init.yml"

#: 远端脚本里由 ssh 那一行传入、不来自 .env 的变量。
PASSED_IN = {"PRODUCT_CODE", "REPO_DIR", "PASSED_SHA"}


def read_value(env_file: Path, key: str) -> subprocess.CompletedProcess[str]:
    # 编码钉死：脚本的报错是中文，跟随系统 locale 会在 GBK 终端上抛异常。
    return subprocess.run(
        ["bash", str(READER), str(env_file), key],
        capture_output=True, encoding="utf-8", errors="replace",
    )


def check_reader() -> list[str]:
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        marker = Path(tmp) / "executed"
        m = marker.as_posix()
        env = Path(tmp) / ".env"
        env.write_bytes((
            "DEPLOY_STAGE=production\n"
            "OIDC_SCOPES=openid profile email phone\n"
            'QUOTED="openid profile email phone"\n'
            "SINGLE='a b'\n"
            f"SUBST=$(touch '{m}')\n"
            f"CHAIN=x; touch '{m}'\n"
            f"BACKTICK=`touch '{m}'`\n"
            "WITH_EQUALS=postgresql://u:p@h/db?a=b\n"
            "CRLF_VALUE=tail\r\n"
            "DUP=first\n"
            "DUP=second\n"
            "EMPTY=\n"
        ).encode("utf-8"))

        expected = {
            "DEPLOY_STAGE": "production",
            "OIDC_SCOPES": "openid profile email phone",
            "QUOTED": "openid profile email phone",
            "SINGLE": "a b",
            "SUBST": f"$(touch '{m}')",
            "CHAIN": f"x; touch '{m}'",
            "BACKTICK": f"`touch '{m}'`",
            "WITH_EQUALS": "postgresql://u:p@h/db?a=b",
            "CRLF_VALUE": "tail",
            "DUP": "second",
            "EMPTY": "",
        }
        for key, want in expected.items():
            done = read_value(env, key)
            if done.returncode != 0 or done.stdout != want:
                failures.append(
                    f"{key}: 退出码 {done.returncode}，得到 {done.stdout!r}，期望 {want!r}"
                    f"{(' — ' + done.stderr.strip()) if done.stderr.strip() else ''}"
                )

        if read_value(env, "NOT_THERE").returncode == 0:
            failures.append("缺失的键应当非零退出，而不是给一个空串让调用方静默继续")
        if read_value(env, f"BAD KEY;touch '{m}'").returncode == 0:
            failures.append("非法键名应当被拒绝")
        if marker.exists():
            failures.append(".env 里的命令被执行了——读取器在执行配置文件，而不是读它")
    return failures


def check_workflow() -> list[str]:
    text = DB_INIT.read_text(encoding="utf-8")
    match = re.search(r"<<'REMOTE_SCRIPT'\n(.*?)\n\s*REMOTE_SCRIPT\n", text, re.S)
    if not match:
        return ["db-init.yml 里找不到 REMOTE_SCRIPT 远端脚本——护栏的静态检查失去了对象"]
    code = "\n".join(
        line for line in match.group(1).splitlines() if not line.strip().startswith("#")
    )

    failures: list[str] = []
    if re.search(r"(^|[;&|\s])(\.|source)\s+\S*\.env\b", code):
        failures.append("远端脚本仍在 source .env——配置文件会被当作 shell 执行")
    if "deploy/database/env-value.sh" not in code:
        failures.append("远端脚本没有经 deploy/database/env-value.sh 读取 .env")

    used = set(re.findall(r"\$\{?([A-Z][A-Z0-9_]*)", code)) - PASSED_IN
    read = set(re.findall(r'^\s*([A-Z][A-Z0-9_]*)="\$\(env_value \1\)"', code, re.M))
    if not used:
        failures.append("远端脚本里一个 .env 变量都没找到——提取规则失效，检查会空转")
    missing = used - read
    if missing:
        failures.append(f"远端脚本用到、却没有经 env_value 读取的变量：{sorted(missing)}")
    return failures


def main() -> int:
    failures = check_reader() + check_workflow()
    if failures:
        print("db-init .env 读取护栏失败：")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("db-init .env 读取一致：只按键取值、不执行文件内容，远端脚本的每个配置变量都经读取器取得。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
