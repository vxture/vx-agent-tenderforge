-- 最小权限服务角色（治理规范 §7 / data_platform_100 §2.2.4）。
--
-- 运行时以 tenderforge_svc 连接，**不是库 owner**：四个 schema 上给
-- SELECT/INSERT/DELETE，**不给 DDL**，**不给整表 UPDATE**——列级 UPDATE
-- 由 98_column_locks.sql 的白名单单独授予。
--
-- 口令在 bootstrap 时注入，**永远不进版本库**（宿主机 .env 的
-- DATABASE_PASSWORD）。这个文件只负责「这个角色能做什么」，不负责它是谁。
--
-- search_path 设在角色上而不是连接串上：应用侧 19 个 JdbcTemplate 类里的
-- SQL 全是不带 schema 前缀的表名，靠它解析。设在角色上意味着换连接池、
-- 换连接串都不会把它丢掉——而丢掉的表现是「relation does not exist」，
-- 一个和权限无关的报错。

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tenderforge_svc') THEN
    CREATE ROLE tenderforge_svc LOGIN;
  END IF;
END $$;

ALTER ROLE tenderforge_svc SET search_path = bid, vx_provision, local_authz, local_usage, public;

GRANT USAGE ON SCHEMA bid, vx_provision, local_authz, local_usage TO tenderforge_svc;

GRANT SELECT, INSERT, DELETE ON ALL TABLES IN SCHEMA bid, vx_provision, local_authz, local_usage
  TO tenderforge_svc;

-- bid_source_segment 用 BIGINT 自增主键，序列要单独给。
-- 漏掉它的表现是插入报 "permission denied for sequence"，而那条错误信息
-- 不会提到是哪张表——只会说某个 *_id_seq。
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA bid TO tenderforge_svc;
