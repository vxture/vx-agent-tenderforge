#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""每个用户命令的第一件事是 C2 权益判定。

背景：权益此前只被读出来给界面看，没有任何一条命令会因为权益不足而被拒绝——
任何能登录的平台用户都能用全部功能。判定点现在在应用服务入口（EntitlementGuard）。
这道护栏回答的是「新加一个命令方法却忘了判定」怎么被拦下：那不会有任何症状，
新功能对未订阅的人照常开放。

检查三件事：

1. 命令服务的每个 public 方法，**第一条语句**是 ``guard.require(``——或者在下面的放行名单里、
   带着理由。「第一条」不是洁癖：判定写在取数之后，未订阅的人会先拿到 404/409，
   看不出自己其实是没权限；写在副作用之后，就是先做了再拒绝。
2. 放行名单里的每个名字都真实存在。方法删了名单还留着，下一个人会以为它仍被刻意放行。
3. 控制器只经这三个服务进入命令——不直接调用下层的 Bid*CommandService，
   否则判定被绕过而这里看不见。

这是静态检查，必要而不充分；充分的那一半是 EntitlementEnforcementIntegrationTest，
它对每个端点真发请求。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE_DIR = (ROOT / "backend" / "project-name-java" / "project-name-application-command"
               / "src" / "main" / "java" / "com" / "td" / "czghagent" / "application" / "command" / "service")
REST_DIR = (ROOT / "backend" / "project-name-java" / "project-name-web"
            / "src" / "main" / "java" / "com" / "td" / "czghagent" / "rest")

#: 服务 → {不判定的 public 方法: 理由}
UNGATED: dict[str, dict[str, str]] = {
    "BidCommandService": {
        "pauseGeneration": "停止消耗不该被拦——失效的人也必须能停下正在跑的生成",
        "removeAsset": "清理自己的数据不该被拦",
    },
    "BidProductionService": {
        # 调用方：BidInterpretationProcessor（Temporal 活动）与 BidSourceCommandService
        # （已在门面判定过的保存解读）。活动里判定会把已放行的任务中途打断。
        "synchronizeInterpretation": "内部回写（解读活动、保存解读），不是用户命令入口",
        # 调用方：BidOutlineCommandService（已在门面判定过的保存目录）。
        "markOutlineReview": "内部标记（保存目录之后），不是用户命令入口",
    },
    "BidLayoutService": {},
}

#: 下层服务：控制器不得直接调用，否则绕过判定。
LOWER_SERVICES = ("BidSourceCommandService", "BidOutlineCommandService",
                  "BidContentCommandService", "BidAssetCommandService")

METHOD = re.compile(r"^    public (?!record |class |static )[\w<>.,\s\[\]]+?\s(\w+)\(", re.M)


def method_body(text: str, start: int) -> str:
    open_brace = text.index("{", start)
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace + 1:index]
    raise ValueError("unbalanced braces")


def first_statement(body: str) -> str:
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("//"):
            continue
        return stripped
    return ""


def check_services() -> list[str]:
    failures: list[str] = []
    for service, ungated in UNGATED.items():
        path = SERVICE_DIR / f"{service}.java"
        text = path.read_text(encoding="utf-8")
        found: set[str] = set()
        for match in METHOD.finditer(text):
            name = match.group(1)
            if name == service:  # constructor
                continue
            found.add(name)
            if name in ungated:
                continue
            first = first_statement(method_body(text, match.end()))
            if not first.startswith("guard.require("):
                failures.append(
                    f"{service}.{name} 的第一条语句不是 guard.require(...)（是：{first[:80]!r}）"
                    "——要么判定权益，要么进放行名单并写明理由"
                )
        if not found:
            failures.append(f"{service} 里一个 public 方法都没解析到——提取规则失效，检查会空转")
        for stale in sorted(set(ungated) - found):
            failures.append(f"放行名单里的 {service}.{stale} 已不存在——名单过期")
    return failures


def check_controllers() -> list[str]:
    failures: list[str] = []
    for path in sorted(REST_DIR.glob("*Controller.java")):
        text = path.read_text(encoding="utf-8")
        for lower in LOWER_SERVICES:
            if re.search(rf"\b{lower}\b", text):
                failures.append(f"{path.name} 直接引用了 {lower}——绕过了命令入口的权益判定")
    return failures


def main() -> int:
    failures = check_services() + check_controllers()
    if failures:
        print("C2 权益判定护栏失败：")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    gated = sum(1 for _ in UNGATED)
    print(f"C2 权益判定一致：{gated} 个命令服务的每个入口方法先判定权益，放行名单均带理由且仍然存在。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
