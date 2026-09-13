#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-13
"""C3 下发的对外回调路径，六处必须说的是同一件事。

通则把这个路径定死成**所有产品同一个**：`POST {你的域名}/api/webhooks/vxture`，
变的只有域名。本仓写下它的地方有六个，各自独立：

  1. `ProductIdentity.PLATFORM_WEBHOOK_PATH`  —— 源码里的唯一取值
  2. 控制器的 `@RequestMapping`               —— 实际挂载点
  3. `AuthenticationFilter` 的会话豁免名单     —— 决定请求能不能走到控制器
  4. `deploy/nginx/default.conf`              —— 决定请求能不能走到容器
  5. `.env.example` 与两份文档                 —— 交给平台去登记的那个值

**它们错开的表现，每一种都不像「路径配错了」。** 漏掉 3，平台的投递被登录过滤器
挡在验签之前，平台只看见一个非 2xx、重试十次、放弃。漏掉 4，请求落到 nginx 末尾的
SPA catch-all，平台拿到 **index.html 和 HTTP 200**——投递被判为送达，产品什么都没
收到，开通与档位变更静默消失，两侧都不报错。漏掉 5，登记的地址和实现的地址不是
一个，而平台侧的登记接口只校验协议与长度、不看路径，上线检查单也只问「填了没有」。

**为什么第 1 条要单独钉字面量。** 其余五处都比对常量，所以常量本身的取值没有任何
一处能证伪——把它改成 `/webhook`，全仓依然自洽、测试依然全绿，只是不再合规。
所以这里把通则规定的字面量写死在守卫里：这份文件是规范在本仓的落点，改它是一次
会进 diff 的显式动作，而不是跟着代码一起漂走。

本仓此前取的是 `/provisioning/webhook`，依据是「vxtpl 和 yucer 都这么写」。查下去
那两处路由的注释都标着「product_200 section 4」，而那一节通篇只规定义务、
**从没规定过路径**——两个产品各自造了同一个名字，又互相成了对方的先例。

**第 4b 条管的是迁移本身，不是取值。** 平台侧登记的还是旧地址，所以 nginx 上留着
一条把旧路径转到标准路径的别名——这是 X-4 的第 ① 步。它的删除条件不靠人记得：
`LEGACY_INBOUND_PATH` 置成 `None` 的那一刻，这条判据会反过来要求别名必须消失。

反证做法（每条判据都这么验过，确认会红）：
  - 把常量改成 `/provisioning/webhook`      → 第 1 条红
  - 控制器改回字面量                         → 第 2 条红
  - 豁免名单里删掉那一行                     → 第 3 条红
  - nginx 把 `location /api/` 删掉           → 第 4 条红
  - nginx 删掉迁移期别名                     → 第 4b 条红
  - 别名转到旧的控制器路径                   → 第 4b 条红
  - LEGACY_INBOUND_PATH 置 None 但别名还在   → 第 4b 条红
  - 文档里留一个旧地址                       → 第 5 条红
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

#: 通则 §C3 下发 规定的路径。所有产品同一个，只有域名不同。
#: 这是本守卫唯一不从代码里读的值——见文件头「为什么第 1 条要单独钉字面量」。
STANDARD_PATH = "/api/webhooks/vxture"

#: 产品在平台登记的边缘域名，用来拼出文档里应当出现的完整 URL。
EDGE_DOMAIN = "tenderforge.vxture.com"
FULL_URL = f"https://{EDGE_DOMAIN}{STANDARD_PATH}"

JAVA = ROOT / "backend" / "project-name-java"
IDENTITY = (
    JAVA
    / "project-name-domain/src/main/java/com/td/czghagent/domain/model/ProductIdentity.java"
)
CONTROLLER = (
    JAVA
    / "project-name-web/src/main/java/com/td/czghagent/rest/ProvisioningWebhookController.java"
)
FILTER = (
    JAVA
    / "project-name-web/src/main/java/com/td/czghagent/rest/security/AuthenticationFilter.java"
)
NGINX = ROOT / "deploy" / "nginx" / "default.conf"

#: 必须出现完整 URL 的三处「交给平台登记」的地方。
URL_SITES = [
    ROOT / ".env.example",
    ROOT / "docs" / "30-design" / "10-detailed-design.md",
    ROOT / "docs" / "50-deployment" / "10-deployment-plan.md",
]

CONSTANT_REF = "ProductIdentity.PLATFORM_WEBHOOK_PATH"

#: 本仓与兄弟仓用过的非标准取值。出现在**投递地址**上即为偏离。
RETIRED_PATHS = ["/api/platform/provisioning/webhook", "/provisioning/webhook"]

#: 迁移期允许保留的**入站别名**，或 `None` 表示迁移已完成。
#:
#: 平台侧当前登记的还是旧地址，所以 nginx 上要留一条别名把它转到标准路径——
#: 这是 X-4 的第 ① 步「先同时收两个路径」。**不留这条就是跳过第 ① 步直接做第 ②**：
#: 发版那一刻起，平台按旧地址投递会落到 SPA catch-all 拿回 index.html 和 HTTP 200，
#: 投递被判为送达而产品什么都没收到，两侧都不报错。
#:
#: **第 ③ 步的删除条件就写在这里**：平台侧把登记地址改成标准路径之后，
#: 把这个常量置成 `None`——守卫会立刻反过来要求 nginx 上那条 location 必须消失。
#: 这样「什么时候能删」不靠人记得，它是一处会进 diff 的显式动作。
LEGACY_INBOUND_PATH: str | None = "/provisioning/webhook"


def read(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"!! 找不到 {path.relative_to(ROOT)}——这个守卫的范围定义已经过期")
    return path.read_text(encoding="utf-8")


def declared_path(text: str) -> str | None:
    """从 ProductIdentity.java 里取出常量的字面量取值。"""
    match = re.search(
        r'PLATFORM_WEBHOOK_PATH\s*=\s*"([^"]*)"\s*;', text
    )
    return match.group(1) if match else None


def main() -> int:
    problems: list[str] = []

    # ── 1. 常量本身的取值 ───────────────────────────────────────────────
    identity_text = read(IDENTITY)
    declared = declared_path(identity_text)
    if declared is None:
        problems.append(
            "ProductIdentity 里没有 PLATFORM_WEBHOOK_PATH 常量——"
            "路径又散回字面量了，这个守卫的其余五条就都失去了锚点"
        )
    elif declared != STANDARD_PATH:
        problems.append(
            f"ProductIdentity.PLATFORM_WEBHOOK_PATH = {declared!r}，"
            f"通则规定的是 {STANDARD_PATH!r}。"
            "所有产品同一个路径，变的只有域名——这个值不是本产品能自拟的"
        )

    # ── 2. 控制器挂载点取常量，不是又抄一遍 ─────────────────────────────
    controller_text = read(CONTROLLER)
    if f"@RequestMapping({CONSTANT_REF})" not in controller_text:
        problems.append(
            f"控制器的 @RequestMapping 没有取 {CONSTANT_REF}。"
            "写成字面量的话，它与豁免名单分叉时没有任何一处会报错"
        )

    # ── 3. 会话豁免名单取同一个常量 ─────────────────────────────────────
    filter_text = read(FILTER)
    if f"{CONSTANT_REF}.equals(path)" not in filter_text:
        problems.append(
            f"AuthenticationFilter 的豁免名单没有取 {CONSTANT_REF}。"
            "少了这一条，平台的投递会被登录过滤器挡在验签之前——"
            "控制器里的代码一行都没跑，而平台只看见一个非 2xx"
        )

    # ── 4. nginx 真的会把它转到 api ─────────────────────────────────────
    nginx_text = read(NGINX)
    if not STANDARD_PATH.startswith("/api/"):
        problems.append(
            f"{STANDARD_PATH} 不在 /api/ 下，而 nginx 只为 /api/ 配了转发规则。"
            "不在任何 location 里命中的路径会落到末尾的 SPA catch-all，"
            "平台会拿到 index.html 和 HTTP 200"
        )
    elif not re.search(
        r"location\s+/api/\s*\{[^}]*proxy_pass\s+http://api:8081\s*;", nginx_text, re.S
    ):
        problems.append(
            "deploy/nginx/default.conf 里没有把 /api/ 转给 api:8081 的 location——"
            f"{STANDARD_PATH} 会落到 SPA catch-all，平台拿到 index.html 和 HTTP 200，"
            "投递被判为送达而产品什么都没收到"
        )
    if not re.search(r"location\s+/\s*\{[^}]*try_files[^}]*index\.html", nginx_text, re.S):
        problems.append(
            "nginx 里找不到末尾的 SPA catch-all——它是上面那条判据的前提，"
            "前提没了说明这份配置已经改过形，判据要重新写"
        )
    # ── 4b. 迁移期别名：该在时必须在且转对，该走时必须走 ─────────────────
    alias_re = re.compile(
        r"location\s+=\s+(\S+)\s*\{[^}]*proxy_pass\s+http://api:8081(\S*?)\s*;",
        re.S,
    )
    aliases = {m.group(1): m.group(2) for m in alias_re.finditer(nginx_text)}
    if LEGACY_INBOUND_PATH is None:
        for retired in RETIRED_PATHS:
            if retired in nginx_text:
                problems.append(
                    f"迁移已标记完成（LEGACY_INBOUND_PATH = None），但 nginx 里还留着 "
                    f"{retired}。留着两条路，下一个人无法从代码判断线上登记的是哪一个"
                )
    else:
        target = aliases.get(LEGACY_INBOUND_PATH)
        if target is None:
            problems.append(
                f"nginx 上缺少迁移期别名 `location = {LEGACY_INBOUND_PATH}`。"
                "平台侧登记的还是旧地址，少了这条别名，发版那一刻平台的投递就会落到 "
                "SPA catch-all 拿回 index.html 和 HTTP 200——投递被判为送达而我们"
                "什么都没收到。这是跳过 X-4 第 ① 步直接做第 ②"
            )
        elif target != STANDARD_PATH:
            problems.append(
                f"迁移期别名 `location = {LEGACY_INBOUND_PATH}` 转到了 {target!r}，"
                f"应当转到 {STANDARD_PATH!r}。两条路必须进同一个控制器——"
                "否则会得到两份各自演进的验签与幂等逻辑，而它们不一致时的症状"
                "取决于平台当天登记的是哪个地址"
            )
        # 除了这一个别名，不该再有第二个通往 api 的旧路径入口。
        for path in aliases:
            if path in RETIRED_PATHS and path != LEGACY_INBOUND_PATH:
                problems.append(
                    f"nginx 上有第二个旧路径别名 `location = {path}`——"
                    "迁移期只登记了一个，多出来的那个没有删除条件管着"
                )

    # ── 5. 交给平台登记的那个值，三处必须是完整标准 URL ─────────────────
    for site in URL_SITES:
        text = read(site)
        rel = site.relative_to(ROOT).as_posix()
        if FULL_URL not in text:
            problems.append(f"{rel} 里没有出现投递地址 {FULL_URL}")
        for retired in RETIRED_PATHS:
            stale = f"https://{EDGE_DOMAIN}{retired}"
            if stale in text:
                problems.append(
                    f"{rel} 里还写着旧投递地址 {stale}——"
                    "平台按文档登记，登记错了的表现是投递全部 404 或落到 SPA"
                )

    # ── 6. Java 源码里不该再有任何一处旧路径字面量 ──────────────────────
    for java_file in JAVA.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8", errors="replace")
        for retired in RETIRED_PATHS:
            if f'"{retired}"' in text:
                problems.append(
                    f"{java_file.relative_to(ROOT).as_posix()} 里还有旧路径字面量 "
                    f'"{retired}"'
                )

    if problems:
        print(f"!! C3 回调路径不一致（{len(problems)} 处）")
        for problem in problems:
            print(f"     {problem}")
        print(f"   通则规定：POST {{你的域名}}{STANDARD_PATH}，所有产品同一个路径。")
        print("   本仓的唯一取值是 ProductIdentity.PLATFORM_WEBHOOK_PATH，其余五处都比对它。")
        return 1

    print(
        f"C3 回调路径一致：常量 = {STANDARD_PATH}（通则规定值），"
        "控制器、豁免名单、nginx 转发、三处登记地址全部对齐。"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
