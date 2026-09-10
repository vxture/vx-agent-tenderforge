#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-09
""".env.example 必须与 docker-compose.yml 逐键对齐。

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
COMPOSE = ROOT / "docker-compose.yml"
EXAMPLE = ROOT / ".env.example"
APPLICATION_YML = (
    ROOT / "backend" / "project-name-java" / "project-name-start"
    / "src" / "main" / "resources" / "application.yml"
)

#: compose 引用但<b>刻意</b>不进 .env.example 的键。
#:
#: 每一条都要写清楚理由——这个集合是给守卫开的口子，开得越随意，
#: 守卫越接近装饰品。
#: compose 引用但<b>刻意</b>不写进 .env.example 的键——它们不是运维旋钮，
#: 而是 deploy.sh 在部署那一刻导出到环境里的值（shell 环境的优先级高于 .env）。
#: 写进 .env.example 会引人在宿主机 .env 里钉一个 tag，而那正是「宿主机上跑的
#: 到底是哪个镜像」这个问题不该有的第二个答案。
_DEPLOY_INJECTED = (
    "部署期注入：deploy.sh 从 CI 传来的 IMAGE_TAG / registry 导出到环境，"
    "不经过宿主机 .env。本地不设时 compose 回落到 ghcr.io/vxture/...:local。"
)

COMPOSE_ONLY: dict[str, str] = {
    "IMAGE_REGISTRY": _DEPLOY_INJECTED,
    "IMAGE_NAMESPACE": _DEPLOY_INJECTED,
    "IMAGE_TAG": _DEPLOY_INJECTED,
    "DATA_DIR": (
        "持久数据的宿主机位置，由 deploy.sh 导出（默认 <stack_root>/data，"
        "也就是 md0 阵列上）。不写进 .env.example：它不是一个可调的旋钮，"
        "写进去会引人把数据指到系统盘上，而那件事不会有任何报错。"
    ),
}

#: application.yml 引用但<b>刻意</b>不放进 compose 白名单的键。
_BUILD_INJECTED = (
    "构建期注入（治理规范 025 §4.1）：由 Dockerfile 的 ARG→ENV 提供，"
    "刻意<b>不</b>放进 compose 的 environment。它回答的是「这是哪一次构建」，"
    "一旦能被宿主机 .env 改写，就不再是那个问题的答案了。"
)

APPLICATION_ONLY: dict[str, str] = {
    "SERVER_PORT": (
        "8081 在健康检查与 nginx 上游里是写死的；放开这个开关等于给人一个"
        "能把整栈弄坏的旋钮，而它坏掉的表现是容器健康但代理 502。"
    ),
    "APP_VERSION": _BUILD_INJECTED,
    "GIT_SHA": _BUILD_INJECTED,
    "BUILD_TIME": _BUILD_INJECTED,
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
        "交付时这些配置不会写进宿主机 .env。补进 .env.example。",
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
