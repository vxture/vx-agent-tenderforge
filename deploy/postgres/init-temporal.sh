#!/bin/sh
# GENERATED_BY_AI
# MODEL: claude-opus-5
# DATE: 2026-09-10
#
# Temporal 自己的两个库与角色。它与产品库同实例、不同库——Temporal 的 schema
# 由它自己的 auto-setup 管，与本产品的 DDL 单一权威互不相干，混在一个库里
# 会让「谁能改这些表」这个问题失去答案。
#
# **角色必须带 CREATEDB，哪怕两个库在这里已经建好了。** auto-setup 启动时自己会
# 调一次 CREATE DATABASE；库已存在它能接受，但**没有建库权限**它不接受——报的是
# `pq: permission denied to create database`，然后进程退出。
#
# 这个失败在编排层的表现极具误导性：schema 会被一路升级到最新版（日志里几十行
# "Schema updated from x to y" 全是成功），最后一步才拒绝，于是容器启动约 4 秒后
# 退出，compose 只说 `dependency failed to start: container ... is unhealthy`。
# 看健康检查的重试参数（10s × 30）会以为是超时，其实根本没轮到重试。
#
# 幂等：整个脚本可以重复跑。CREATE DATABASE 没有 IF NOT EXISTS，所以先查
# 再建——不查直接建，第二次启动就会以「database already exists」失败，
# 而那时它是 restart: "no" 的一次性 job，失败会挡住整条依赖链。
set -eu

export PGPASSWORD="${POSTGRES_PASSWORD}"
PSQL="psql -h db -U postgres -v ON_ERROR_STOP=1 -q"

$PSQL <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'temporal') THEN
    CREATE ROLE temporal LOGIN CREATEDB PASSWORD '${TEMPORAL_DB_PASSWORD}';
  ELSE
    ALTER ROLE temporal LOGIN CREATEDB PASSWORD '${TEMPORAL_DB_PASSWORD}';
  END IF;
END \$\$;
SQL

for dbname in temporal temporal_visibility; do
  exists=$($PSQL -tAc "SELECT 1 FROM pg_database WHERE datname = '${dbname}'")
  if [ -z "$exists" ]; then
    $PSQL -c "CREATE DATABASE ${dbname} OWNER temporal"
  else
    $PSQL -c "ALTER DATABASE ${dbname} OWNER TO temporal"
  fi
done

echo "[temporal-db-init] temporal / temporal_visibility 就绪"
