# 结构增量

`00_baseline.sql` 是 **create-once** 的：永远不要在它里面 ALTER 一张已存在的表。
结构变更放这里，编号递增、`NNNN_slug.sql`，由 `apply.sh` 按文件名顺序施加。

## 硬性要求

**每个增量必须自己幂等**——`ADD COLUMN IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS`、
带 `IF NOT EXISTS` 的约束添加。理由是 `apply.sh` 会把 `incr/` 整个重放一遍：
不幂等的增量第二次施加就会失败，而失败发生在一次紧急的结构变更中间。

**改结构的 PR 必须同批带上对应增量**（治理规范 §7 硬性项）。只改基线不写增量 =
新库有、活库没有，而代码是按新结构写的——功能在生产上直接坏掉，而构建和测试
全绿。判据是机器可验的：diff 命中 `ddl/00_baseline.sql` 而 `incr/` 无新增 = 未达标。

## 新增可写列时

同批改 `98_column_locks.sql` 的白名单。不改的表现是服务写 `permission denied`，
而那是有意的——让「我加了一列」必须同时回答「谁能写它」。

## 施加

只走 `db-init.yml`（`confirm=yes` + `expected_sha` + 生产环境审批门）。
不要在宿主机上手跑 `apply.sh`——那样这次变更不会有任何记录。
