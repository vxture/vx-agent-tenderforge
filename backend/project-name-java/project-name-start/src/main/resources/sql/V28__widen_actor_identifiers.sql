-- GENERATED_BY_AI
-- MODEL: claude-opus-5
-- DATE: 2026-09-08
--
-- 把存放「人」的列从本地 UUID 尺寸放宽到平台 subject 尺寸。
--
-- 【为什么必须改】
-- 这些列一律是 VARCHAR(36)，因为本地账号的 id 是 UUID。接上平台身份后，
-- 它们存的是 IdP 的 sub —— 而 sub 不是 UUID：平台的运营主体形如 opr_<uuid>，
-- 假名化后的终端用户是 sha256 的十六进制串，联邦身份还可能更长。
--
-- 【它是怎么被发现的】
-- OIDC 骨架第一次真的走完一遍登录回路时，写登录审计直接炸了：
-- Data too long for column 'actor_id'。在那之前所有测试都用本地 UUID，
-- 所以这个尺寸假设从来没有被触碰过——一个只在接真身份的那一刻才暴露的约束。
--
-- 【为什么是 255】
-- 与 rp_session.subject 对齐。不追求「够用就行」的紧尺寸：这些列上的索引
-- 是前缀可用的，放宽不改变查询计划，而每一次因为差几个字符再发一次迁移，
-- 成本都远高于现在多给的那点空间。
--
-- 【必须先拆外键】
-- 这些列上挂着指向 app_user 的外键，MySQL 不允许改被外键引用的列（错误 1832）。
-- 但外键本身才是更根本的问题：它断言「每一个归属人都是一条本地账号记录」，
-- 而平台身份下归属人是 IdP 的 subject，<b>根本没有 app_user 行</b>。
-- 保留它意味着要给每个登录用户建一条影子账号——那等于凭空造出第二份身份真源，
-- 正是「身份来自平台、本地只存业务角色」这条决定要避免的东西。
--
-- 归属关系不再由数据库外键保证，改由应用层保证：写入时归属人取自
-- 已验证的会话（CurrentUser），从不取自请求体。调用方自报归属等于没有隔离，
-- 这条纪律比外键更强——外键只能保证「这个 id 在某张表里存在」，
-- 保证不了「这个 id 是当前调用者」。
--
-- 【语法约束】沿用 V26/V27：一条语句一列，避开 MySQL 专有的多列合并 ALTER。

-- ── 0. 拆掉指向本地账号表的外键 ────────────────────────────────────────────
ALTER TABLE bid_document DROP FOREIGN KEY fk_bid_document_owner;
ALTER TABLE bid_reference_asset DROP FOREIGN KEY fk_bid_asset_owner;
ALTER TABLE bid_generation_snapshot DROP FOREIGN KEY fk_bid_snapshot_owner;
ALTER TABLE bid_chapter_version DROP FOREIGN KEY fk_bid_chapter_version_user;
ALTER TABLE bid_interpretation_version DROP FOREIGN KEY fk_bid_interpretation_user;
ALTER TABLE bid_export DROP FOREIGN KEY fk_bid_export_user;
ALTER TABLE bid_layout_job DROP FOREIGN KEY fk_bid_layout_user;
ALTER TABLE bid_requirement_conflict DROP FOREIGN KEY fk_bid_conflict_user;

-- fk_user_session_user 保留：user_session 只服务本地口令登录，
-- 它的 user_id 确实应该指向一条 app_user 记录，两者会一起退役。

-- 审计：动作发起者与对象标识都可能是平台 subject。
ALTER TABLE audit_log MODIFY COLUMN actor_id VARCHAR(255) NULL
    COMMENT '动作发起者（X-3 actorId）；平台 subject 或本地用户 id，系统自发动作为空';
ALTER TABLE audit_log MODIFY COLUMN object_id VARCHAR(255) NULL
    COMMENT '对象标识（X-3 objectId）；对 USER 类对象即平台 subject';

-- 业务归属：标书与素材的所有者在平台身份下就是 subject。
ALTER TABLE bid_document MODIFY COLUMN owner_id VARCHAR(255) NOT NULL
    COMMENT '归属人；平台 subject 或本地用户 id';
ALTER TABLE bid_reference_asset MODIFY COLUMN owner_id VARCHAR(255) NOT NULL
    COMMENT '归属人；平台 subject 或本地用户 id';
ALTER TABLE bid_generation_snapshot MODIFY COLUMN owner_id VARCHAR(255) NOT NULL
    COMMENT '归属人；平台 subject 或本地用户 id';

-- 留痕列：谁做的这一次冻结/导出/排版。
ALTER TABLE bid_chapter_version MODIFY COLUMN created_by VARCHAR(255) NULL
    COMMENT '创建者；平台 subject 或本地用户 id';
ALTER TABLE bid_interpretation_version MODIFY COLUMN created_by VARCHAR(255) NULL
    COMMENT '创建者；平台 subject 或本地用户 id';
ALTER TABLE bid_export MODIFY COLUMN created_by VARCHAR(255) NULL
    COMMENT '创建者；平台 subject 或本地用户 id';
ALTER TABLE bid_layout_job MODIFY COLUMN created_by VARCHAR(255) NULL
    COMMENT '创建者；平台 subject 或本地用户 id';

-- bid_requirement_conflict.resolved_by 也放宽：它同样记「哪个人处理的」。
ALTER TABLE bid_requirement_conflict MODIFY COLUMN resolved_by VARCHAR(255) NULL
    COMMENT '处理人；平台 subject 或本地用户 id';

-- user_session.user_id 刻意不动：那张表只服务本地口令登录，
-- 它的 user_id 有指向 app_user 的语义，会随本地账号体系一起退役。
-- 放宽它等于暗示它将来也要装 subject，而那是另一张表的职责（rp_session）。
