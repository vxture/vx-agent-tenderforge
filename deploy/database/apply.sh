#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
#
# clean-baseline 的 DDL 施加器。**只由 db-init.yml 调用**——容器 entrypoint
# 永远不迁移数据库（治理规范 §11：常规部署链不跑 migration/seed）。
#
# 顺序：三段基线 → incr/ 下的编号增量，逐个 fail-fast。
# 基线是 create-once 的（CREATE TABLE IF NOT EXISTS），所以重复施加是幂等的；
# 增量必须自己写成幂等的（ADD COLUMN IF NOT EXISTS 之类），见 incr/README.md。
set -euo pipefail

DDL_DIR="$(cd "$(dirname "$0")/ddl" && pwd)"
DB_URL="${DATABASE_URL:?DATABASE_URL is required}"

for f in 00_baseline.sql 97_service_role.sql 98_column_locks.sql; do
  echo "[ddl] applying ${f}"
  psql "${DB_URL}" -v ON_ERROR_STOP=1 -q -f "${DDL_DIR}/${f}"
done

shopt -s nullglob
for f in "${DDL_DIR}"/incr/*.sql; do
  echo "[ddl] applying incr $(basename "${f}")"
  psql "${DB_URL}" -v ON_ERROR_STOP=1 -q -f "${f}"
done

echo "[ddl] done."
