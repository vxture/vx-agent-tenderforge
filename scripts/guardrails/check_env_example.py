#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
"""deploy/.env.example 必须与 docker-compose.yml 逐键对齐。

为什么值得一条 CI 守卫：`.env.example` 是 `ENV_FILE_BASE64` 的起点，也是
「本产品需要哪些配置」的唯一来源。而 compose 的 `environment:` 是**白名单**
——不在那里列出的变量写进 `.env` 完全没有反应，服务照常起来、照常用默认值、
没有任何报错。

两个方向都要查，因为两个方向都出过事：

* **compose 用了、example 没写**：交付时那些配置压根不会写进宿主机 `.env`。
  有阶段守卫的会拒绝启动（好事），没守卫的静默走默认值。
  这份仓库真实出现过一次，一口气差了 36 个键。
* **example 写了、compose 没列**：运维照着填了值，而它到不了任何容器。
  这种「我配了但没生效」最难查，因为配置文件里白纸黑字写着。

另外还查 application.yml 引用的每个环境变量都在 compose 白名单里——
那是同一个失效链条的上游。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deploy" / "docker-compose.yml"
EXAMPLE = ROOT / "deploy" / ".env.example"
APPLICATION_YML = (
    ROOT / "backend" / "project-name-java" / "project-name-start"
    / "src" / "main" / "resources" / "application.yml"
)

#: compose 引用但<b>刻意</b>不进 .env.example 的键。
#:
#: 每一条都要写清楚理由——这个集合是给守卫开的口子，开得越随意，
#: 守卫越接近装饰品。
COMPOSE_ONLY: dict[str, str] = {}

#: application.yml 引用但<b>刻意</b>不放进 compose 白名单的键。
APPLICATION_ONLY: dict[str, str] = {
    "SERVER_PORT": (
        "8081 在健康检查与 nginx 上游里是写死的；放开这个开关等于给人一个"
        "能把整栈弄坏的旋钮，而它坏掉的表现是容器健康但代理 502。"
    ),
}


def compose_environment_keys(text: str) -> set[str]:
    """compose 的 environment: 段里声明的键。

    只认「缩进 + 大写键名 + 冒号」这种真正的声明。<b>不能用子串匹配</b>：
    注释里提到一个变量名就会被当成已声明——这条守卫自己第一版就是这么
    误判过一次，把 SERVER_PORT 报成「已列出」。
    """
    return set(re.findall(r"^\s+([A-Z][A-Z0-9_]*):\s", text, re.M))


def compose_referenced_keys(text: str) -> set[str]:
    """compose 里 ${VAR} 形式引用的键。"""
    return set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)[:}]", text))


def env_example_keys(text: str) -> set[str]:
    """.env.example 里定义的键（忽略注释行）。"""
    keys: set[str] = set()
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        match = re.match(r"^([A-Z][A-Z0-9_]*)=", stripped)
        if match:
            keys.add(match.group(1))
    return keys


def application_yml_keys(text: str) -> set[str]:
    return set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)[:}]", text))


def report(title: str, keys: set[str], hint: str) -> bool:
    if not keys:
        return True
    print(f"\n!! {title}（{len(keys)} 个）")
    for key in sorted(keys):
        print(f"     {key}")
    print(f"   {hint}")
    return False


def main() -> int:
    for path in (COMPOSE, EXAMPLE, APPLICATION_YML):
        if not path.exists():
            print(f"!! 找不到 {path.relative_to(ROOT)}")
            return 1

    compose_text = COMPOSE.read_text(encoding="utf-8")
    example_text = EXAMPLE.read_text(encoding="utf-8")
    application_text = APPLICATION_YML.read_text(encoding="utf-8")

    referenced = compose_referenced_keys(compose_text)
    declared = compose_environment_keys(compose_text)
    example = env_example_keys(example_text)
    application = application_yml_keys(application_text)

    ok = True
    ok &= report(
        "compose 引用了、.env.example 没写",
        referenced - example - set(COMPOSE_ONLY),
        "交付时这些配置不会写进宿主机 .env。补进 deploy/.env.example。",
    )
    ok &= report(
        ".env.example 写了、compose 没引用",
        example - referenced,
        "运维会填上值，而它到不了任何容器。要么补进 compose，要么从模板删掉。",
    )
    ok &= report(
        "application.yml 引用了、compose 没在 environment 里声明",
        application - declared - set(APPLICATION_ONLY),
        "compose 的 environment: 是白名单，不在其中的变量写进 .env 毫无反应。",
    )

    if ok:
        print(
            f"env 模板一致：compose 引用 {len(referenced)} 个键，"
            f".env.example 覆盖 {len(example)} 个，"
            f"application.yml 的 {len(application)} 个引用全部在白名单内。"
        )
        return 0
    print("\n配置模板与实际需要不一致——见上。")
    return 1


if __name__ == "__main__":
    sys.exit(main())
