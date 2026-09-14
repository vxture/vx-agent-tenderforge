#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
#
# clean-baseline 的 DDL 施加器。**只由 db-init.yml 调用**——容器 entrypoint
# 永远不迁移数据库（治理规范 §11：常规部署链不跑 migration/seed）。
#
# 顺序：三段基线 → incr/ 下的编号增量，逐个 fail-fast。
# 整份 DDL 必须可以在活库上重放：建表建索引用 IF NOT EXISTS，外键包在
# duplicate_object 守卫里（PostgreSQL 的 ADD CONSTRAINT 没有 IF NOT EXISTS）；
# 增量同样必须自己幂等，见 incr/README.md。这件事由 PostgresBackedTest 在同一个
# 库上施加两遍来守——此前这里写着「所以重复施加是幂等的」，而它从没被验过，
# 2026-09-15 在生产上第一次重放就倒在了第一条外键上。
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
