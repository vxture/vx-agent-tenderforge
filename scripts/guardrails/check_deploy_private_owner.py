#!/usr/bin/env python3
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-14
"""部署时 `data/private` 的属主必须等于 api 镜像的运行用户。

真实事故（2026-09-14，生产）：`deploy.sh` 以部署用户（uid 1000）`mkdir` 出
`data/private`，而 api / worker 以镜像里的 `app`（uid 10001）运行，目录是 775
——容器能读不能写。服务照常 healthy、部署照常报绿，直到第一次有人上传招标文件，
才在日志里出现 `AccessDeniedException: /app/data/private/bids`。探针只探 liveness，
所以从部署到出事之间没有任何信号。

**本护栏跑的是 deploy.sh 本身**，对着真实的 Docker：造两个运行用户 uid 不同的
探针镜像，分别让脚本去修属主，再读目录的真实属主。用两个 uid 是为了拦住
「把 10001 写死在脚本里」——写死的版本对第一个镜像全绿，对第二个才红。

另查一件静态的事：`cmd_start` 必须在 `compose up` **之前**调用它。函数再对，
没接进部署路径就等于没有，而漏接不会有任何症状。

没有 Docker 时**失败而不是跳过**：一个在缺依赖时自动变绿的护栏，
恰好会在最需要它的那台机器上什么都不查。
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SH = ROOT / "deploy" / "deploy.sh"

BASE_IMAGE = "alpine:3.20"
PROBE_REGISTRY = "local"
PROBE_NAMESPACE = "tf-owner-probe"
PROBE_UIDS = (10001, 10002)


def run(args: list[str], **kwargs: object) -> subprocess.CompletedProcess[str]:
    # 编码钉死：deploy.sh 输出中文，跟随系统 locale 会在 GBK 终端上抛异常。
    return subprocess.run(
        args, capture_output=True, encoding="utf-8", errors="replace", **kwargs  # type: ignore[call-overload]
    )


def probe_tag(uid: int) -> str:
    return f"uid{uid}"


def probe_image(uid: int) -> str:
    # 与 deploy.sh 里的拼法一致：${IMAGE_REGISTRY}/${IMAGE_NAMESPACE}/${PRODUCT_CODE}-api:${IMAGE_TAG}
    return f"{PROBE_REGISTRY}/{PROBE_NAMESPACE}/tenderforge-api:{probe_tag(uid)}"


def build_probe(uid: int) -> None:
    dockerfile = f"FROM {BASE_IMAGE}\nRUN adduser -D -u {uid} app\nUSER app\n"
    done = run(["docker", "build", "-q", "-t", probe_image(uid), "-"], input=dockerfile)
    if done.returncode != 0:
        raise SystemExit(f"FATAL: 构建探针镜像 uid={uid} 失败：\n{done.stderr}")


def deploy_owner(data_dir: Path, uid: int) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env.update(
        DATA_DIR=str(data_dir),
        IMAGE_REGISTRY=PROBE_REGISTRY,
        IMAGE_NAMESPACE=PROBE_NAMESPACE,
        IMAGE_TAG=probe_tag(uid),
    )
    return run(["bash", str(DEPLOY_SH), "owner"], env=env)


def remove_as_root(data_dir: Path) -> None:
    """探针目录属于容器 uid，运行护栏的用户删不掉它，得借容器删。"""
    run(["docker", "run", "--rm", "--user", "0", "-v", f"{data_dir}:/t", BASE_IMAGE,
         "rm", "-rf", "/t/private"])
    shutil.rmtree(data_dir, ignore_errors=True)


def check_runtime(uid: int) -> list[str]:
    failures: list[str] = []
    data_dir = Path(tempfile.mkdtemp(prefix="tf-owner-"))
    private = data_dir / "private"
    try:
        # ① 目录已存在、属于运行护栏的用户——正是事故现场的形状。
        private.mkdir()
        done = deploy_owner(data_dir, uid)
        owner = private.stat().st_uid
        if done.returncode != 0 or owner != uid:
            failures.append(
                f"uid={uid} 既有目录：退出码 {done.returncode}，属主 {owner}，期望 {uid}\n"
                f"{done.stdout}{done.stderr}"
            )

        # ② 再跑一次必须是空操作——每次部署都会经过这里。
        again = deploy_owner(data_dir, uid)
        if again.returncode != 0 or "已是" not in again.stdout:
            failures.append(
                f"uid={uid} 重复执行不是空操作：退出码 {again.returncode}\n"
                f"{again.stdout}{again.stderr}"
            )

        # ③ 目录不存在：`start` 可以不经 `directories` 单独跑，那时要自己建。
        remove_as_root(data_dir)
        data_dir.mkdir()
        fresh = deploy_owner(data_dir, uid)
        owner = private.stat().st_uid if private.is_dir() else None
        if fresh.returncode != 0 or owner != uid:
            failures.append(
                f"uid={uid} 目录不存在：退出码 {fresh.returncode}，属主 {owner}，期望 {uid}\n"
                f"{fresh.stdout}{fresh.stderr}"
            )
    finally:
        remove_as_root(data_dir)
    return failures


def check_wired_into_start() -> list[str]:
    text = DEPLOY_SH.read_text(encoding="utf-8")
    match = re.search(r"^cmd_start\(\) \{\n(.*?)^\}", text, re.S | re.M)
    if not match:
        return ["deploy.sh 里找不到 cmd_start()——护栏的静态检查失去了对象"]
    code = "\n".join(
        line for line in match.group(1).splitlines() if not line.lstrip().startswith("#")
    )
    owner_at = code.find("ensure_private_owner")
    up_at = code.find("compose up")
    if owner_at == -1:
        return ["cmd_start 没有调用 ensure_private_owner——函数没接进部署路径"]
    if up_at == -1:
        return ["cmd_start 里找不到 compose up——护栏的顺序检查失去了参照"]
    if owner_at > up_at:
        return ["cmd_start 在 compose up 之后才修属主——容器启动时目录仍不可写"]
    return []


def main() -> int:
    if shutil.which("docker") is None or run(["docker", "info"]).returncode != 0:
        print("FATAL: 本护栏需要可用的 Docker——它验的是脚本对真实容器 uid 的行为")
        return 1
    if hasattr(os, "getuid") and os.getuid() in PROBE_UIDS:
        print(f"FATAL: 护栏以 uid {os.getuid()} 运行，与探针 uid 相同，检查会空转")
        return 1

    failures = check_wired_into_start()
    try:
        for uid in PROBE_UIDS:
            build_probe(uid)
            failures += check_runtime(uid)
    finally:
        for uid in PROBE_UIDS:
            run(["docker", "rmi", "-f", probe_image(uid)])

    if failures:
        print("private 目录属主护栏失败：")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print(f"private 目录属主一致：探针 uid {PROBE_UIDS} 均修正、重复执行为空操作、目录缺失时自建。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
