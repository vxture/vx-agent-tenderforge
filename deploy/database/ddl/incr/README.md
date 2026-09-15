# 结构增量

`00_baseline.sql` 是 **create-once** 的：永远不要在它里面 ALTER 一张已存在的表。
结构变更放这里，编号递增、`NNNN_slug.sql`，由 `apply.sh` 按文件名顺序施加。

施加顺序是 **基线 → 增量 → `97_service_role` → `98_column_locks`**：权限排在结构之后，
增量新加的表才拿得到 97 的授权，新加的列才让得了 98 的 GRANT。

## 硬性要求

**每个增量必须自己幂等**——`ADD COLUMN IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`；
约束添加包进 `DO $$ BEGIN … EXCEPTION WHEN duplicate_object THEN NULL; END $$`
（PostgreSQL 的 `ADD CONSTRAINT` **没有** `IF NOT EXISTS`，写法照基线末尾的外键段）。
理由是 db-init 每次都把基线与 `incr/` 整个重放一遍：不幂等的增量第二次施加就会失败，
而失败发生在一次紧急的结构变更中间。

这条由 `PostgresBackedTest` 在同一个库上施加两遍来守（基线 + 增量）：写出不可重放的
DDL，全部集成测试起不来。此前这里写着「带 `IF NOT EXISTS` 的约束添加」——那个语法
不存在，而基线的 47 条外键因此从来不可重放，直到 2026-09-15 db-init 在生产上第一次重放。

**改结构的 PR 必须同批带上对应增量**（治理规范 §7 硬性项）。只改基线不写增量 =
新库有、活库没有，而代码是按新结构写的——功能在生产上直接坏掉，而构建和测试
全绿。判据是机器可验的：diff 命中 `ddl/00_baseline.sql` 而 `incr/` 无新增 = 未达标。

## 新增可写列时

同批改 `98_column_locks.sql` 的白名单。不改的表现是服务写 `permission denied`，
而那是有意的——让「我加了一列」必须同时回答「谁能写它」。

列只加在增量里、不回写基线：这样新库与活库走同一条路径，`PostgresBackedTest`
施加的就是活库上那一遍（例：`0001_bid_document_metered_characters.sql`）。

## 施加

只走 `db-init.yml`（`confirm=yes` + `expected_sha` + 生产环境审批门）。
不要在宿主机上手跑 `apply.sh`——那样这次变更不会有任何记录。
