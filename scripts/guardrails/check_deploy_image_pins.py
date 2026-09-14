#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
"""宿主机上手工执行 `docker compose`，必须解析到本次部署的镜像。

真实事故（2026-09-15，生产）：compose 里的镜像是
`${IMAGE_REGISTRY}/${IMAGE_NAMESPACE}/…:${IMAGE_TAG}`，三个变量只在 CI 部署时导出，
宿主机 .env 里没有。为改一个环境变量在主机上 `docker compose up -d api`，镜像被解析成
`ghcr.io/vxture/tenderforge-api:local`——拉取被拒、转去本地构建、因主机上没有源码而失败。
更坏的结局是主机上恰好有个 :local 镜像，服务被悄悄换成它。

deploy.sh 在拉取成功后、up 之前生成 docker-compose.override.yml 钉住镜像。本护栏：

1. **跑 deploy.sh 本身**（`pin` 子命令），然后在**不导出 IMAGE_*** 的环境里读真实的
   `docker compose config`，断言四个服务解析到部署引用；连续钉两个 tag，断言后一次
   是覆盖而不是追加或残留。先跑一次无钉子的对照，确认那时确实是 :local——否则
   断言对着一个本来就相等的值空转。
2. **静态断言 cmd_start 的顺序**：拉取失败检查 → write_image_pins → compose up。
   钉在拉取之前会钉住本地没有的引用；钉在 up 之后，up 失败时钉子与现场不一致。
3. **两个 workflow 的 rsync --delete 都排除它**，.gitignore 忽略它。

没有 docker compose 时失败而不是跳过。
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SH = ROOT / "deploy" / "deploy.sh"
COMPOSE = ROOT / "docker-compose.yml"
GITIGNORE = ROOT / ".gitignore"
WORKFLOWS = [ROOT / ".github" / "workflows" / "deploy.yml",
             ROOT / ".github" / "workflows" / "rollback.yml"]
OVERRIDE = "docker-compose.override.yml"

#: compose 服务 → 它运行的镜像名后缀（worker 与 api 共用一个镜像）。
SERVICES = {"api": "api", "worker": "api", "ai": "ai", "web": "web"}
PROBE_REGISTRY = "registry.example.invalid"
PROBE_NAMESPACE = "probe-ns"
PROBE_TAGS = ("sha-aaaaaaa", "sha-bbbbbbb")
IMAGE_VARS = ("IMAGE_REGISTRY", "IMAGE_NAMESPACE", "IMAGE_TAG",
              "FALLBACK_IMAGE_REGISTRY", "FALLBACK_IMAGE_NAMESPACE")


def run(args: list[str], env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    # 编码钉死：deploy.sh 输出中文，跟随系统 locale 会在 GBK 终端上抛异常。
    return subprocess.run(args, capture_output=True, encoding="utf-8", errors="replace", env=env)


def without_image_vars() -> dict[str, str]:
    """一个人在主机上手工操作时的环境：没有任何 IMAGE_*。"""
    env = dict(os.environ)
    for key in IMAGE_VARS:
        env.pop(key, None)
    return env


def resolved_images(stack: Path) -> dict[str, str] | str:
    done = run(["docker", "compose", "--project-directory", str(stack), "config", "--format", "json"],
               env=without_image_vars())
    if done.returncode != 0:
        return f"docker compose config 失败：{done.stderr.strip()[:300]}"
    services = json.loads(done.stdout)["services"]
    return {name: services[name]["image"] for name in SERVICES}


def check_runtime() -> list[str]:
    failures: list[str] = []
    stack = Path(tempfile.mkdtemp(prefix="tf-pins-"))
    try:
        (stack / "deploy").mkdir()
        shutil.copy(COMPOSE, stack / "docker-compose.yml")
        shutil.copy(DEPLOY_SH, stack / "deploy" / "deploy.sh")
        # compose 对这几个键写的是 ${VAR:?}，缺了 config 直接拒绝——给占位值即可。
        (stack / ".env").write_text(
            "DEPLOY_STAGE=production\nPOSTGRES_ROOT_PASSWORD=x\n"
            "TEMPORAL_DB_PASSWORD=x\nDATABASE_PASSWORD=x\n", encoding="utf-8")

        control = resolved_images(stack)
        if isinstance(control, str):
            return [control]
        if not all(image.endswith(":local") for image in control.values()):
            failures.append(f"对照失败：没有钉子时镜像不是 :local（{control}），检查会空转")

        for tag in PROBE_TAGS:
            env = without_image_vars()
            env.update(REPO_DIR=stack.as_posix(), IMAGE_REGISTRY=PROBE_REGISTRY,
                       IMAGE_NAMESPACE=PROBE_NAMESPACE, IMAGE_TAG=tag)
            done = run(["bash", str(stack / "deploy" / "deploy.sh"), "pin"], env=env)
            if done.returncode != 0:
                failures.append(f"deploy.sh pin（{tag}）退出码 {done.returncode}：{done.stderr.strip()[:300]}")
                continue
            expected = {name: f"{PROBE_REGISTRY}/{PROBE_NAMESPACE}/tenderforge-{suffix}:{tag}"
                        for name, suffix in SERVICES.items()}
            actual = resolved_images(stack)
            if actual != expected:
                failures.append(f"钉 {tag} 之后，不导出 IMAGE_* 的 compose 解析为 {actual}，期望 {expected}")

        leftovers = [p.name for p in stack.iterdir() if p.name.startswith(".docker-compose.override.")]
        if leftovers:
            failures.append(f"生成留下了临时文件：{leftovers}")
    finally:
        shutil.rmtree(stack, ignore_errors=True)
    return failures


def check_order() -> list[str]:
    text = DEPLOY_SH.read_text(encoding="utf-8")
    match = re.search(r"^cmd_start\(\) \{\n(.*?)^\}", text, re.S | re.M)
    if not match:
        return ["deploy.sh 里找不到 cmd_start()——顺序检查失去了对象"]
    code = "\n".join(line for line in match.group(1).splitlines()
                     if not line.lstrip().startswith("#"))
    pulled = code.find('[ "$rc" -eq 0 ]')
    pinned = code.find("write_image_pins")
    up = code.find("compose up")
    if pulled == -1 or up == -1:
        return ["cmd_start 里找不到拉取失败检查或 compose up——顺序检查失去了参照"]
    if pinned == -1:
        return ["cmd_start 没有调用 write_image_pins——手工 compose 会解析回 :local"]
    failures = []
    if pinned < pulled:
        failures.append("write_image_pins 在拉取检查之前——会钉住一个本地可能不存在的引用")
    if pinned > up:
        failures.append("write_image_pins 在 compose up 之后——up 失败时钉子与现场不一致")
    return failures


def check_delivery() -> list[str]:
    failures = []
    for workflow in WORKFLOWS:
        # 一条 rsync 命令 = 以反斜杠续行的若干行 + 最后一行。写成「行尾是反斜杠的行」
        # 而不是「任意字符再跟可选续行」：后者的 [^\n]* 会贪婪吞掉行尾那个反斜杠，
        # 续行组于是匹配零次，只抓到第一行——第一版就这样把两个已经排除了的 rsync
        # 报成了「没排除」。换行统一成 LF，免得 Windows 检出的 CRLF 让续行匹配落空。
        text = workflow.read_text(encoding="utf-8").replace("\r\n", "\n")
        blocks = re.findall(r"rsync -az --delete(?:[^\n]*\\\n)*[^\n]*", text)
        if not blocks:
            failures.append(f"{workflow.name} 里找不到 rsync --delete——排除检查失去了对象")
        for block in blocks:
            if f"--exclude='{OVERRIDE}'" not in block:
                failures.append(f"{workflow.name} 的 rsync --delete 没有排除 {OVERRIDE}——每次投递都会删掉钉子")
    ignored = [line.strip() for line in GITIGNORE.read_text(encoding="utf-8").splitlines()]
    if OVERRIDE not in ignored:
        failures.append(f".gitignore 没有忽略 {OVERRIDE}——主机生成物可能被提交，本地开发也会被它改写镜像")
    return failures


def main() -> int:
    if shutil.which("docker") is None or run(["docker", "compose", "version"]).returncode != 0:
        print("FATAL: 本护栏需要 docker compose——它验的是 compose 实际解析出的镜像")
        return 1
    failures = check_order() + check_delivery() + check_runtime()
    if failures:
        print("部署镜像钉子护栏失败：")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("部署镜像钉子一致：不导出 IMAGE_* 时四个服务解析到部署引用，生成顺序与投递排除均正确。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
