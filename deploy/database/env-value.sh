#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-15
#
# 从 compose 的 .env 里读一个键的值，**不执行文件的任何内容**。
#
#   bash deploy/database/env-value.sh <env 文件> <KEY>
#
# 为什么不 `set -a; . ./.env`：.env 是 docker compose 的配置格式，不是 shell 脚本。
# compose 允许未加引号的空格值，bash 却会把 `OIDC_SCOPES=openid profile email phone`
# 拆成「临时赋值 + 执行 profile 命令」——2026-09-15 db-init 就是这样在生产上以 127
# 退出的，一条 DDL 都没施加，而服务一直正常（compose 读同一行没问题）。
# source 还意味着配置文件里任何 `$(...)`、`;`、反引号都会被当命令执行。
#
# 语义（与 compose 对齐到本仓用得到的程度）：
#   * 按 `KEY=` 行首匹配，同一个键出现多次取最后一个
#   * 值两端成对的双引号或单引号被剥掉；不做转义与插值
#   * 行尾的 CR 被剥掉（在 Windows 上编辑过的 .env）
#   * 键不存在 → 非零退出；值为空 → 输出空串，是否允许由调用方决定
#
# scripts/guardrails/check_db_init_env_reader.py 用一份带空格、引号、命令替换的
# .env 跑这个脚本本身，并断言其中的命令一条都没有被执行。
set -euo pipefail

file="${1:?usage: env-value.sh <env file> <KEY>}"
key="${2:?usage: env-value.sh <env file> <KEY>}"

if ! [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "env-value: 非法键名 '$key'" >&2
  exit 2
fi

line="$(grep -E "^${key}=" "$file" | tail -n 1 || true)"
if [ -z "$line" ]; then
  echo "env-value: $file 里没有 $key" >&2
  exit 1
fi

value="${line#*=}"
value="${value%$'\r'}"
if [ "${#value}" -ge 2 ]; then
  first="${value:0:1}"
  last="${value: -1}"
  if { [ "$first" = '"' ] || [ "$first" = "'" ]; } && [ "$first" = "$last" ]; then
    value="${value:1:${#value}-2}"
  fi
fi

printf '%s' "$value"
