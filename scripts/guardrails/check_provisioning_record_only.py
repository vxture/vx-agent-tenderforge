#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""C3 下发：开通记录只是记录，丢一条投递的代价只能是延迟。

通则 C3 下发原文：「webhook 是提示，不是权威。……不要把 `tenant.provisioned`
当成唯一的建账触发——**启动时与定期各对账一次**当前应有的开通关系。这样任何一条投递
丢失的代价都只是延迟，而不是『客户付了钱而什么都没有』。」

**这条义务本产品目前靠构造满足，而不是靠一个对账任务。** 满足它的是下面这个事实链：

  1. 开通事件唯一的落点 `platform_workspace_provision` 没有任何人读它的状态——
     读的只有 `last_seq`，用来丢弃乱序的旧事件；
  2. 开通时不初始化任何业务空间——标书是用户按需建的，没有「该建而没建」的东西；
  3. 门控只有 C2 一处，按平台给的 `max-age=45` 缓存。

所以一条 `tenant.provisioned` 丢了，用户最多在 45 秒缓存过期后经 C2 看到自己的权益——
正是通则说的「代价只是延迟」。**对账任务在这里没有东西可对**：拿 C2 去刷一张没人读的表，
等于造出第二份真相，还是一份没有 seq、会和 webhook 互相覆盖的真相。

**这个构造一旦被打破，对账就从「无事可做」变成「必须做」**，而打破它的改动每一种都
看起来很自然：给门控加一句「开通记录是 provisioned 才放行」、开通时顺手建个默认空间、
把权益缓存拉长到 10 分钟省几次请求。它们都不会让任何测试变红，只会让一条丢失的投递
从延迟变成永久缺失——平台 10 次重投用尽之后不再发。

所以这里把事实链逐条钉住。要打破其中任何一条，先实现启动时与定期对账，再把
`RECONCILER` 指向它。「列出本产品应有的开通关系」平台目前没有接口，已回平台提
vxture-platform/vxture-platform#342——不在本仓自造替代。

反证做法（每条判据都这么验过，确认会红）：
  - 仓储 SELECT 开通表的 state                 → 第 1 条红
  - 服务层 JDBC 直接读开通表                    → 第 1 条红
  - 仓储接口加一个读状态的方法                  → 第 2 条红
  - 开通服务多注入一个业务依赖                  → 第 3 条红
  - 权益缓存拉长到 600 秒                       → 第 4 条红
  - RECONCILER 指向一个不存在的类               → 第 5 条红
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

JAVA = ROOT / "backend" / "project-name-java"
PYTHON = ROOT / "backend" / "project-name-python"
MAIN = "src/main/java/com/td/czghagent"

REPOSITORY_IMPL = (
    JAVA / f"project-name-infrastructure/{MAIN}/infrastructure/repository/JdbcProvisioningRepository.java"
)
REPOSITORY_PORT = (
    JAVA / f"project-name-domain/{MAIN}/domain/repository/ProvisioningRepository.java"
)
COMMAND_SERVICE = (
    JAVA
    / f"project-name-application-command/{MAIN}/application/command/service/ProvisioningCommandService.java"
)
ENTITLEMENT_RESOLVER = (
    JAVA / f"project-name-infrastructure/{MAIN}/infrastructure/platform/PlatformEntitlementResolver.java"
)

STATE_TABLE = "platform_workspace_provision"

#: 开通表上唯一允许被读出来的列。seq 只用于丢弃乱序事件，不决定任何行为。
READABLE_COLUMNS = {"last_seq"}

#: 仓储接口允许的方法。多一个就是有人要读开通状态了。
PORT_METHODS = {"claimDelivery", "recordOutcome", "lastSeq", "upsertInstance"}

#: 开通服务允许注入的依赖：记录、驱逐权益缓存、审计。多一个就是开通时开始做事了。
SERVICE_DEPENDENCIES = {"ProvisioningRepository", "EntitlementResolver", "AuditRepository"}

#: 通则 C2：响应头 `Cache-Control: private, max-age=45`。
#: 丢一条投递的最大延迟就是它——拉长它就是拉长「付了钱看不到」的窗口。
MAX_ENTITLEMENT_TTL_SECONDS = 45

#: 启动时与定期对账的实现类（相对 JAVA 的路径），或 `None` 表示本产品靠构造满足。
#:
#: 置为 `None` 时第 1–4 条生效；指向一个类时第 1–3 条放开（开通状态有人读、开通时做事
#: 都可以了），第 5 条要求那个类真的存在。先写对账再放开，顺序不能反过来。
RECONCILER: str | None = None


def read(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"!! 找不到 {path.relative_to(ROOT)}——这个守卫的范围定义已经过期")
    return path.read_text(encoding="utf-8")


def production_sources() -> list[Path]:
    """会进生产镜像、且可能连库的源码。测试、DDL 与文档不算。"""
    java = [p for p in JAVA.rglob("*.java") if "/src/main/" in p.as_posix()]
    python = [
        p
        for p in PYTHON.rglob("*.py")
        if "/tests/" not in p.as_posix() and "/.venv/" not in p.as_posix()
    ]
    return java + python


def main() -> int:
    problems: list[str] = []

    if RECONCILER is None:
        # ── 1. 开通表只有仓储碰，且只读 last_seq ────────────────────────────
        for source in production_sources():
            if source == REPOSITORY_IMPL:
                continue
            if STATE_TABLE in source.read_text(encoding="utf-8", errors="replace"):
                problems.append(
                    f"{source.relative_to(ROOT).as_posix()} 直接引用了 {STATE_TABLE}。"
                    "开通表只该经 ProvisioningRepository 进出——绕过它读状态，"
                    "丢一条投递就不再只是延迟"
                )
        impl_text = read(REPOSITORY_IMPL)
        selects = re.findall(
            rf"SELECT\s+(.*?)\s+FROM\s+{STATE_TABLE}\b", impl_text, re.S | re.I
        )
        if not selects:
            problems.append(
                f"JdbcProvisioningRepository 里找不到对 {STATE_TABLE} 的 SELECT——"
                "乱序判定读 last_seq 的那一句不见了，判据要重新写"
            )
        for columns in selects:
            read_columns = {c.strip() for c in columns.split(",")}
            extra = read_columns - READABLE_COLUMNS
            if extra:
                problems.append(
                    f"JdbcProvisioningRepository 从 {STATE_TABLE} 读出了 "
                    f"{', '.join(sorted(extra))}。开通状态一旦有人读，"
                    "它就成了 webhook 驱动的真相，而平台 10 次重投用尽后不再发——"
                    "先实现启动时与定期对账，再把 RECONCILER 指向它"
                )

        # ── 2. 仓储接口没有读状态的口子 ─────────────────────────────────────
        port_text = read(REPOSITORY_PORT)
        methods = set(re.findall(r"^\s+\S[^=;(]*?\s(\w+)\s*\([^)]*\)\s*;", port_text, re.M))
        if methods != PORT_METHODS:
            added = methods - PORT_METHODS
            missing = PORT_METHODS - methods
            problems.append(
                "ProvisioningRepository 的方法变了"
                + (f"，多了 {', '.join(sorted(added))}" if added else "")
                + (f"，少了 {', '.join(sorted(missing))}" if missing else "")
                + "。多出来的读方法就是门控或业务要依赖开通记录了——"
                "那需要对账兜底，不能只靠 webhook"
            )

        # ── 3. 开通时不做业务动作 ───────────────────────────────────────────
        service_text = read(COMMAND_SERVICE)
        constructor = re.search(
            r"public\s+ProvisioningCommandService\s*\(([^)]*)\)", service_text, re.S
        )
        if constructor is None:
            problems.append("找不到 ProvisioningCommandService 的构造器，判据要重新写")
        else:
            dependencies = set(re.findall(r"(\w+)\s+\w+\s*(?:,|$)", constructor.group(1).strip()))
            if dependencies != SERVICE_DEPENDENCIES:
                problems.append(
                    "ProvisioningCommandService 的依赖变成了 "
                    f"{', '.join(sorted(dependencies))}（应为 "
                    f"{', '.join(sorted(SERVICE_DEPENDENCIES))}）。开通时一旦开始建东西，"
                    "丢一条 tenant.provisioned 就是「付了钱什么都没有」——"
                    "先实现对账，再把 RECONCILER 指向它"
                )

    # ── 4. 丢一条投递的最大延迟 = 权益缓存时长（无论有没有对账都成立） ─────
    resolver_text = read(ENTITLEMENT_RESOLVER)
    ttl = re.search(r"\bTTL\s*=\s*Duration\.ofSeconds\((\d+)\)", resolver_text)
    if ttl is None:
        problems.append(
            "PlatformEntitlementResolver 里找不到 `TTL = Duration.ofSeconds(N)`——"
            "缓存时长改了写法，判据要重新写"
        )
    elif int(ttl.group(1)) > MAX_ENTITLEMENT_TTL_SECONDS:
        problems.append(
            f"权益缓存 TTL 是 {ttl.group(1)} 秒，平台给的是 max-age={MAX_ENTITLEMENT_TTL_SECONDS}。"
            "它就是一条投递丢失后用户看不到权益的最长时间"
        )

    # ── 5. 声明了对账，就得真有 ─────────────────────────────────────────
    if RECONCILER is not None and not (JAVA / RECONCILER).exists():
        problems.append(
            f"RECONCILER 指向 {RECONCILER}，但这个文件不存在——"
            "第 1–3 条已经因此放开，而放开的前提并不成立"
        )

    if problems:
        print(f"!! C3 下发：丢一条投递的代价不再只是延迟（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print("   通则：webhook 是提示；不要把 tenant.provisioned 当唯一建账触发，启动时与定期各对账一次。")
        print("   平台侧缺「列出应有开通关系」的接口：vxture-platform/vxture-platform#342")
        return 1

    if RECONCILER is None:
        print(
            "C3 下发靠构造满足：开通表只读 last_seq、开通时不做业务动作、"
            f"门控只经 C2（缓存 ≤ {MAX_ENTITLEMENT_TTL_SECONDS} 秒）。丢一条投递的代价只是延迟。"
        )
    else:
        print(f"C3 下发由对账兜底：{RECONCILER}；权益缓存 ≤ {MAX_ENTITLEMENT_TTL_SECONDS} 秒。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
