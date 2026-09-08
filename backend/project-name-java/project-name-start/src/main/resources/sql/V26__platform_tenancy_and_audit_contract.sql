-- GENERATED_BY_AI
-- MODEL: claude-opus-5
-- DATE: 2026-09-08
--
-- 平台接入 P1：租户轴与审计字段契约。
--
-- 两件事合并成一次迁移，因为它们改的是同一批行的同一次读写路径：
-- 审计行既要改字段名，又要带上租户轴，分两次迁移会让中间态出现
-- 「新名字 + 无租户」的一批行，而那批行在任何一侧都解释不了。
--
-- 【语法约束：每条语句只做一件事】
-- 生产库是 MySQL 8.4，集成测试库是 H2 的 MySQL 兼容模式。两者的交集比 MySQL 窄：
-- 多列合并的 ALTER（ADD COLUMN a, ADD COLUMN b）在 H2 上直接语法错误。
-- 本文件因此坚持一条语句一列。列改名用 RENAME COLUMN 而不是 MySQL 专有的
-- CHANGE COLUMN——RENAME COLUMN 是 MySQL 8.0 与 H2 都支持的写法，
-- 而 CHANGE COLUMN 只有前者认。这条约束是被一次真实的集成测试启动失败逼出来的。
--
-- 【租户轴落在哪】
-- 只加在聚合根与直接归属实体上：bid_document、bid_reference_asset、audit_log。
-- 其余 30 张 bid_* 子表通过 bid_id 继承——给它们各加一列会得到 30 处可能不一致的
-- 真相，而任何一处漏更新都表现为「数据在租户之间静默串味」。
-- 判据：查询是否需要不经 join 就按租户过滤。
--
-- 【为什么现在就填值而不是留空】
-- 可空的租户键是危险的：忘记加过滤的查询会静默返回全部行，而它看起来完全正常。
-- 所以这里给存量行回填一个显式的过渡值，让查询路径从第一天起就永远带过滤条件。
-- 过渡值形如 local:<owner_id>，刻意不是 UUID —— 它不可能与平台签发的真实
-- workspace 冲突，而且肉眼可辨「这行还没接上平台身份」。
-- P2 接通 OIDC 后由会话提供真实 org/workspace，本前缀随之消失。

-- ── 1. 标书：租户轴 ────────────────────────────────────────────────────────
ALTER TABLE bid_document ADD COLUMN org_id VARCHAR(64) NULL
    COMMENT '平台组织标识；local: 前缀为平台身份接通前的过渡值';
ALTER TABLE bid_document ADD COLUMN workspace_id VARCHAR(64) NULL
    COMMENT '平台工作空间标识，租户隔离键；local: 前缀为过渡值';

UPDATE bid_document SET org_id = CONCAT('local:', owner_id) WHERE org_id IS NULL;
UPDATE bid_document SET workspace_id = CONCAT('local:', owner_id) WHERE workspace_id IS NULL;

ALTER TABLE bid_document MODIFY COLUMN org_id VARCHAR(64) NOT NULL
    COMMENT '平台组织标识；local: 前缀为平台身份接通前的过渡值';
ALTER TABLE bid_document MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL
    COMMENT '平台工作空间标识，租户隔离键；local: 前缀为过渡值';

CREATE INDEX idx_bid_document_workspace ON bid_document (workspace_id, updated_at);

-- ── 2. 个人素材：租户轴 ────────────────────────────────────────────────────
ALTER TABLE bid_reference_asset ADD COLUMN org_id VARCHAR(64) NULL
    COMMENT '平台组织标识；local: 前缀为过渡值';
ALTER TABLE bid_reference_asset ADD COLUMN workspace_id VARCHAR(64) NULL
    COMMENT '平台工作空间标识，租户隔离键；local: 前缀为过渡值';

UPDATE bid_reference_asset SET org_id = CONCAT('local:', owner_id) WHERE org_id IS NULL;
UPDATE bid_reference_asset SET workspace_id = CONCAT('local:', owner_id) WHERE workspace_id IS NULL;

ALTER TABLE bid_reference_asset MODIFY COLUMN org_id VARCHAR(64) NOT NULL
    COMMENT '平台组织标识；local: 前缀为过渡值';
ALTER TABLE bid_reference_asset MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL
    COMMENT '平台工作空间标识，租户隔离键；local: 前缀为过渡值';

CREATE INDEX idx_bid_reference_asset_workspace ON bid_reference_asset (workspace_id, created_at);

-- ── 3. 审计：X-3 最小字段集改名 ────────────────────────────────────────────
--
-- 字段名由《产品接入通则》X-3 规定，不是本仓的偏好：
-- eventId / occurredAt / actorId / actorConsole / objectType / objectId / action / outcome，
-- 消费面另加 taskId / costAmount / costUnit。
-- 「都合规」与「能一起查」不是一回事——跨产品对账靠的正是这些名字一致。
--
-- 先改列名再加新列：反过来的话，中间态会同时存在新旧两套名字，
-- 而这期间任何一次部署回滚都会写进一批半新半旧的行。
ALTER TABLE audit_log RENAME COLUMN id TO event_id;
ALTER TABLE audit_log RENAME COLUMN user_id TO actor_id;
ALTER TABLE audit_log RENAME COLUMN action_code TO action;
ALTER TABLE audit_log RENAME COLUMN target_type TO object_type;
ALTER TABLE audit_log RENAME COLUMN target_id TO object_id;
ALTER TABLE audit_log RENAME COLUMN result_code TO outcome;
ALTER TABLE audit_log RENAME COLUMN created_at TO occurred_at;

-- actor_console：铸造这次换票的工作台 RP。
-- 本方自产的写填产品码常量；不属于任何控制台的后台通道（Temporal worker）填 NULL——
-- 通则明确要求 MUST NOT 硬编一个，因为一个编出来的控制台名会让审计员
-- 按控制台筛查时得到一批根本不是从那里发起的动作。
ALTER TABLE audit_log ADD COLUMN actor_console VARCHAR(64) NULL
    COMMENT '发起动作的控制台 RP（X-3 actorConsole）；后台通道为空';
ALTER TABLE audit_log ADD COLUMN task_id VARCHAR(128) NULL
    COMMENT '跨产品聚合键（X-2 taskId），调用方送来什么就原样存什么';
ALTER TABLE audit_log ADD COLUMN org_id VARCHAR(64) NULL
    COMMENT '平台组织标识';
ALTER TABLE audit_log ADD COLUMN workspace_id VARCHAR(64) NULL
    COMMENT '平台工作空间标识';

-- 存量审计行按发起者回填租户轴。发起者为空的系统行保持为空：
-- 那些行本来就不属于任何工作空间，编一个进去只会让按工作空间筛查多出假记录。
UPDATE audit_log SET org_id = CONCAT('local:', actor_id)
    WHERE actor_id IS NOT NULL AND org_id IS NULL;
UPDATE audit_log SET workspace_id = CONCAT('local:', actor_id)
    WHERE actor_id IS NOT NULL AND workspace_id IS NULL;

-- 索引跟着列名走。旧索引名里编着旧列名，留着会让下一个人读到一个说谎的名字。
DROP INDEX idx_audit_log_user ON audit_log;
DROP INDEX idx_audit_log_action_result_created ON audit_log;
DROP INDEX idx_audit_log_target ON audit_log;

CREATE INDEX idx_audit_log_actor ON audit_log (actor_id, occurred_at);
CREATE INDEX idx_audit_log_action_outcome ON audit_log (action, outcome, occurred_at);
CREATE INDEX idx_audit_log_object ON audit_log (object_type, object_id, occurred_at);
-- 键集游标翻页的支撑索引：ORDER BY occurred_at DESC, event_id DESC 必须走索引，
-- 否则无界表上的每一次翻页都要排序整表。
CREATE INDEX idx_audit_log_cursor ON audit_log (occurred_at, event_id);
CREATE INDEX idx_audit_log_task ON audit_log (task_id);
CREATE INDEX idx_audit_log_workspace ON audit_log (workspace_id, occurred_at);
