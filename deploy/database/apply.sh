#!/usr/bin/env bash
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
#
# clean-baseline 的 DDL 施加器。**只由 db-init.yml 调用**——容器 entrypoint
# 永远不迁移数据库（治理规范 §11：常规部署链不跑 migration/seed）。
#
# 顺序：基线 → incr/ 下的编号增量 → 97 角色 → 98 列锁，逐个 fail-fast。
# 权限排在结构之后：97 按「schema 下全部表」授权、98 按列授权，增量加的表与列
# 必须先存在。2026-09-15 之前增量排在最后，第一个加可写列的增量就会让活库上的
# 98 倒在「列不存在」上。
# 整份 DDL 必须可以在活库上重放：建表建索引用 IF NOT EXISTS，外键包在
# duplicate_object 守卫里（PostgreSQL 的 ADD CONSTRAINT 没有 IF NOT EXISTS）；
# 增量同样必须自己幂等，见 incr/README.md。这件事由 PostgresBackedTest 在同一个
# 库上施加两遍来守——此前这里写着「所以重复施加是幂等的」，而它从没被验过，
# 2026-09-15 在生产上第一次重放就倒在了第一条外键上。
set -euo pipefail

DDL_DIR="$(cd "$(dirname "$0")/ddl" && pwd)"
DB_URL="${DATABASE_URL:?DATABASE_URL is required}"

shopt -s nullglob
for f in "${DDL_DIR}/00_baseline.sql" "${DDL_DIR}"/incr/*.sql \
         "${DDL_DIR}/97_service_role.sql" "${DDL_DIR}/98_column_locks.sql"; do
  echo "[ddl] applying $(basename "${f}")"
  psql "${DB_URL}" -v ON_ERROR_STOP=1 -q -f "${f}"
done

echo "[ddl] done."
