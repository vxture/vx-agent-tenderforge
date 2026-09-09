-- 业务面 DB 基线 —— **单一 DDL 权威**（治理规范 §7 / data_platform_100 §3.2）
--
-- 手写、create-once：**永远不要在这里 ALTER 一张已存在的表**。结构变更以
-- deploy/database/ddl/incr/NNNN_*.sql 的编号增量交付，经 db-init.yml 施加
-- （confirm=yes + expected_sha + 生产环境审批门）。常规部署链不跑任何迁移。
--
-- 这份基线是 2026-09-10 从 MySQL 侧 V1–V30 的**最终形态**一次性转写而来。
-- 之所以可以做 clean-baseline 而不是逐条翻译 30 个迁移：**本产品从未部署过**，
-- 线上不存在任何一个库，没有需要保历史的活库。这个窗口一旦首次上线就没了。
--
-- 命名与列规范（data_platform_100 §3.2.2）：
--   * 主键 UUID，库端默认 gen_random_uuid()
--   * 时间一律 TIMESTAMPTZ（MySQL 侧是无时区 timestamp，语义上一直是 UTC+8，
--     转过来之后时区变成显式的而不是靠注释约定）
--   * 状态/类型用 VARCHAR + CHECK，**不用 PG ENUM**（避免迁移锁与不可回退）
--   * 索引前缀 idx_ / 唯一 uidx_ / 外键 fk_ / 检查 chk_
--
-- schema 划分：三个契约 schema（vx_provision / local_authz / local_usage）
-- 与基准产品 vxtpl 一致，业务表进域 schema bid。运行时角色的 search_path
-- 覆盖这四个，所以应用侧的 SQL 不需要写 schema 前缀。
--
-- 表名保持**单数**。规范 §3.2.1 的目标形态是复数，但同一节明写「存量表单复数
-- 混用待各域逐域改造时统一，改造前不视为违规」，且基准产品 vxtpl 的基线也是
-- 单数。复数化会牵动 19 个 JdbcTemplate 类里的每一条 SQL，单独排期（TD-006）。

CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()

-- ==========================================================================
-- vx_provision  —— 平台驱动的开通状态与入站 webhook 事件账（契约 schema，出厂即有）
-- ==========================================================================
CREATE SCHEMA IF NOT EXISTS vx_provision;

CREATE TABLE IF NOT EXISTS vx_provision.platform_provision_delivery (
  delivery_id   VARCHAR(191) NOT NULL,
  event_type    VARCHAR(128) NOT NULL,
  workspace_id  VARCHAR(255),
  seq           BIGINT NOT NULL,
  outcome       VARCHAR(32) NOT NULL,
  received_at   TIMESTAMPTZ(3) NOT NULL,
  CONSTRAINT pk_platform_provision_delivery PRIMARY KEY (delivery_id)
);
CREATE INDEX IF NOT EXISTS idx_provision_delivery_received ON vx_provision.platform_provision_delivery (received_at);

CREATE TABLE IF NOT EXISTS vx_provision.platform_workspace_provision (
  workspace_id      VARCHAR(255) NOT NULL,
  product           VARCHAR(64) NOT NULL,
  state             VARCHAR(32) NOT NULL,
  last_seq          BIGINT NOT NULL DEFAULT 0,
  provisioned_at    TIMESTAMPTZ(3),
  deprovisioned_at  TIMESTAMPTZ(3),
  updated_at        TIMESTAMPTZ(3) NOT NULL,
  CONSTRAINT pk_platform_workspace_provision PRIMARY KEY (workspace_id, product)
);

-- ==========================================================================
-- local_authz  —— 本地身份与会话（契约 schema）。平台身份接通后这里只剩过渡账号
-- ==========================================================================
CREATE SCHEMA IF NOT EXISTS local_authz;

-- 系统用户表，保存规划编制员和管理员账号，业务实时更新，账号停用后保留审计关联
CREATE TABLE IF NOT EXISTS local_authz.app_user (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),   -- 用户ID，UUID
  username         VARCHAR(64) NOT NULL,   -- 登录名，全系统唯一
  password_hash    VARCHAR(100) NOT NULL,   -- BCrypt密码哈希，敏感信息
  display_name     VARCHAR(64) NOT NULL,   -- 界面显示姓名
  role_code        VARCHAR(24) NOT NULL,   -- 角色：PLANNER=规划编制员，ADMIN=系统管理员
  avatar_url       VARCHAR(500),   -- 头像地址，不存储二进制内容
  enabled          BOOLEAN NOT NULL DEFAULT TRUE,   -- 账号是否可用
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区：UTC+8
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 更新时间，时区：UTC+8
  revision         BIGINT NOT NULL DEFAULT 0,   -- 乐观锁版本号
  avatar_revision  VARCHAR(36),   -- 头像缓存版本，不包含私有存储对象键
  CONSTRAINT pk_app_user PRIMARY KEY (id),
  CONSTRAINT uidx_app_user_username UNIQUE (username)
);

-- OIDC 授权码流程的服务端暂存，一次性使用
CREATE TABLE IF NOT EXISTS local_authz.oidc_authorization_request (
  state          VARCHAR(64) NOT NULL,   -- CSRF 防护随机串，同时是本行主键
  nonce          VARCHAR(64) NOT NULL,   -- 重放防护随机串，必须与 id_token 的 nonce 一致
  code_verifier  VARCHAR(128) NOT NULL,   -- PKCE 验证串，绝不下发浏览器
  return_to      VARCHAR(512),   -- 登录后回跳的站内路径，已白名单化
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间
  expires_at     TIMESTAMPTZ NOT NULL,   -- 过期时间；过期的请求一律拒绝
  CONSTRAINT pk_oidc_authorization_request PRIMARY KEY (state)
);
CREATE INDEX IF NOT EXISTS idx_oidc_authorization_request_expiry ON local_authz.oidc_authorization_request (expires_at);

-- OIDC 依赖方会话；浏览器只持有不透明 cookie
CREATE TABLE IF NOT EXISTS local_authz.rp_session (
  id                 UUID NOT NULL DEFAULT gen_random_uuid(),   -- 会话标识
  token_hash         VARCHAR(64) NOT NULL,   -- cookie 值的 SHA-256，cookie 原值不落库
  subject            VARCHAR(255) NOT NULL,   -- IdP 的 sub，平台内的用户标识
  display_name       VARCHAR(255),   -- 展示名，取自 id_token 的 profile 声明；仅用于渲染
  email              VARCHAR(255),   -- 展示用邮箱；不作为身份键
  picture            VARCHAR(1024),   -- 头像地址；仅用于渲染
  org_id             VARCHAR(64),   -- access token 的 active_org
  workspace_id       VARCHAR(64),   -- access token 的 active_workspace
  roles              VARCHAR(1024),   -- 治理角色，逗号分隔；带 scope 前缀如 workspace:owner
  access_token       TEXT NOT NULL,   -- 平台 access token；换票与调用的原料，绝不下发浏览器
  refresh_token      TEXT,   -- 刷新令牌；轮换时存新弃旧
  access_expires_at  TIMESTAMPTZ NOT NULL,   -- access token 过期时间，静默续期据此提前触发
  expires_at         TIMESTAMPTZ NOT NULL,   -- 会话过期时间
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间
  last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 最近一次使用
  CONSTRAINT pk_rp_session PRIMARY KEY (id),
  CONSTRAINT uidx_rp_session_token UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS idx_rp_session_subject ON local_authz.rp_session (subject);
CREATE INDEX IF NOT EXISTS idx_rp_session_expiry ON local_authz.rp_session (expires_at);

-- 可撤销登录会话表，数据来源：账号登录，失效会话可定期清理
CREATE TABLE IF NOT EXISTS local_authz.user_session (
  id            UUID NOT NULL DEFAULT gen_random_uuid(),   -- 会话ID，UUID
  user_id       UUID NOT NULL,   -- 用户ID，关联app_user.id
  token_hash    VARCHAR(64) NOT NULL,   -- 会话令牌SHA-256哈希，敏感信息
  expires_at    TIMESTAMPTZ NOT NULL,   -- 会话失效时间，时区：UTC+8
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区：UTC+8
  last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 最后访问时间，时区：UTC+8
  CONSTRAINT pk_user_session PRIMARY KEY (id),
  CONSTRAINT uidx_user_session_token UNIQUE (token_hash)
);

-- ==========================================================================
-- local_usage  —— 用量缓冲（契约 schema）。冲洗到平台的中转，不是账本本身
-- ==========================================================================
CREATE SCHEMA IF NOT EXISTS local_usage;

CREATE TABLE IF NOT EXISTS local_usage.platform_usage_event (
  idempotency_key  VARCHAR(191) NOT NULL,
  workspace_id     VARCHAR(255) NOT NULL,
  metric           VARCHAR(128) NOT NULL,
  amount           BIGINT NOT NULL,
  end_user_id      VARCHAR(255),
  task_id          VARCHAR(128),
  occurred_at      TIMESTAMPTZ(3) NOT NULL,
  claim_token      VARCHAR(64),
  claimed_at       TIMESTAMPTZ(3),
  flushed_at       TIMESTAMPTZ(3),
  attempts         INTEGER NOT NULL DEFAULT 0,
  last_error       VARCHAR(512),
  CONSTRAINT pk_platform_usage_event PRIMARY KEY (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_usage_pending ON local_usage.platform_usage_event (flushed_at, claimed_at, occurred_at);
CREATE INDEX IF NOT EXISTS idx_usage_claim ON local_usage.platform_usage_event (claim_token);

-- ==========================================================================
-- bid  —— 标书业务域（产品空白区）
-- ==========================================================================
CREATE SCHEMA IF NOT EXISTS bid;

-- 业务审计日志表，记录关键写操作和下载行为，默认长期保留并限制管理员访问
CREATE TABLE IF NOT EXISTS bid.audit_log (
  event_id        UUID NOT NULL,   -- 审计事件标识（X-3 eventId）
  actor_id        VARCHAR(255),   -- 动作发起者（X-3 actorId）；平台 subject 或本地用户 id，系统自发动作为空
  actor_console   VARCHAR(64),   -- 发起动作的控制台 RP（X-3 actorConsole）；后台通道为空
  action          VARCHAR(64) NOT NULL,   -- 动作名（X-3 action）
  object_type     VARCHAR(64) NOT NULL,   -- 对象类型（X-3 objectType）
  object_id       VARCHAR(255),   -- 对象标识（X-3 objectId）；对 USER 类对象即平台 subject
  outcome         VARCHAR(24) NOT NULL,   -- 结果（X-3 outcome）；必须区分成功与被拒
  task_id         VARCHAR(128),   -- 跨产品聚合键（X-2 taskId），调用方送来什么就原样存什么
  org_id          VARCHAR(64),   -- 平台组织标识
  workspace_id    VARCHAR(64),   -- 平台工作空间标识
  detail_summary  VARCHAR(1000),   -- 操作摘要，不保存密码、令牌、密钥和完整个人信息
  trace_id        VARCHAR(64) NOT NULL,   -- 请求链路ID
  ip_address      VARCHAR(64),   -- 客户端IP，属于个人信息，受访问控制
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 发生时间（X-3 occurredAt）
  CONSTRAINT pk_audit_log PRIMARY KEY (event_id)
);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor ON bid.audit_log (actor_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_outcome ON bid.audit_log (action, outcome, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_object ON bid.audit_log (object_type, object_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_cursor ON bid.audit_log (occurred_at, event_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_task ON bid.audit_log (task_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_workspace ON bid.audit_log (workspace_id, occurred_at);

CREATE TABLE IF NOT EXISTS bid.bid_document (
  id                      UUID NOT NULL DEFAULT gen_random_uuid(),
  owner_id                VARCHAR(255) NOT NULL,   -- 归属人；平台 subject 或本地用户 id
  org_id                  VARCHAR(64) NOT NULL,   -- 平台组织标识；local: 前缀为平台身份接通前的过渡值
  workspace_id            VARCHAR(64) NOT NULL,   -- 平台工作空间标识，租户隔离键；local: 前缀为过渡值
  code                    VARCHAR(40) NOT NULL,
  writing_method          VARCHAR(32) NOT NULL,
  title                   VARCHAR(160) NOT NULL,
  target_pages            INTEGER NOT NULL DEFAULT 100,
  bidding_mode            VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  workflow_step           VARCHAR(24) NOT NULL DEFAULT 'SETUP',
  status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  content_stale           BOOLEAN NOT NULL DEFAULT FALSE,
  error_message           VARCHAR(1000),
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  revision                BIGINT NOT NULL DEFAULT 0,
  interpretation_status   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',   -- 解读阶段状态：DRAFT, REVIEW, FROZEN
  interpretation_version  INTEGER NOT NULL DEFAULT 0,   -- 当前解读版本号，从0开始
  interpretation_hash     CHAR(64),   -- 冻结解读规范化内容的SHA-256
  outline_status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',   -- 目录阶段状态：DRAFT, REVIEW, FROZEN
  outline_version         INTEGER NOT NULL DEFAULT 0,   -- 当前目录版本号，从0开始
  outline_hash            CHAR(64),   -- 冻结目录规范化内容的SHA-256
  content_status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',   -- 正文阶段状态：DRAFT, GENERATING, REVIEW, FROZEN
  content_version         INTEGER NOT NULL DEFAULT 0,   -- 当前成稿版本号，从0开始
  content_hash            CHAR(64),   -- 冻结正文规范化内容的SHA-256
  stale_reason            VARCHAR(1000),   -- 正文需要复核的具体上游变更原因
  CONSTRAINT pk_bid_document PRIMARY KEY (id),
  CONSTRAINT uidx_bid_document_code UNIQUE (code)
);
CREATE INDEX IF NOT EXISTS idx_bid_document_owner ON bid.bid_document (owner_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_bid_document_workspace ON bid.bid_document (workspace_id, updated_at);

CREATE TABLE IF NOT EXISTS bid.bid_generation_task (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id           UUID NOT NULL,
  status           VARCHAR(24) NOT NULL,
  total_units      INTEGER NOT NULL,
  completed_units  INTEGER NOT NULL DEFAULT 0,
  error_message    VARCHAR(1000),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at       TIMESTAMPTZ,
  finished_at      TIMESTAMPTZ,
  workflow_run_id  VARCHAR(160),   -- Temporal工作流运行ID；本地模式为空
  snapshot_hash    CHAR(64),   -- 本次生成使用的冻结输入快照SHA-256
  retry_count      INTEGER NOT NULL DEFAULT 0,   -- 任务级重试次数
  heartbeat_at     TIMESTAMPTZ,   -- 最近活动心跳时间，时区UTC+8
  snapshot_id      UUID,   -- 不可变生成快照ID，新任务必须填写，历史任务为空
  CONSTRAINT pk_bid_generation_task PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_task_latest ON bid.bid_generation_task (bid_id, created_at);

-- 正文任务的不可变生成输入根快照，每个任务创建一次且不更新
CREATE TABLE IF NOT EXISTS bid.bid_generation_snapshot (
  id                      UUID NOT NULL DEFAULT gen_random_uuid(),   -- 不可变生成快照ID
  task_id                 UUID NOT NULL,   -- 生成任务ID，关联bid_generation_task.id
  bid_id                  UUID NOT NULL,   -- 标书ID，关联bid_document.id
  owner_id                VARCHAR(255) NOT NULL,   -- 归属人；平台 subject 或本地用户 id
  snapshot_hash           CHAR(64) NOT NULL,   -- 全部生成输入规范化后的SHA-256
  bid_title               VARCHAR(160) NOT NULL,   -- 快照中的标书标题
  bidding_mode            VARCHAR(16) NOT NULL,   -- 投标方式：OPEN或BLIND
  target_pages            INTEGER NOT NULL,   -- 目标页数，单位：页
  interpretation_version  INTEGER NOT NULL,   -- 冻结解读版本号
  outline_version         INTEGER NOT NULL,   -- 冻结目录版本号
  solution_contract       TEXT NOT NULL,   -- 目录策略冻结形成的解决方案架构契约，供全部正文lane共享
  writing_bible           TEXT NOT NULL,   -- 全文写作总纲，包含主题、语气和章节协同约束
  term_registry           TEXT NOT NULL,   -- 全文统一术语注册表，换行分隔
  commitment_registry     TEXT NOT NULL,   -- 全文统一承诺注册表，换行分隔
  prompt_version          VARCHAR(64) NOT NULL,   -- 正文提示词契约版本
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  CONSTRAINT pk_bid_generation_snapshot PRIMARY KEY (id),
  CONSTRAINT uidx_bid_snapshot_task UNIQUE (task_id),
  CONSTRAINT uidx_bid_snapshot_hash UNIQUE (task_id, snapshot_hash)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_bid ON bid.bid_generation_snapshot (bid_id, created_at);

-- 统一AI调用审计，只记录元数据、哈希、耗时和Token，不记录API密钥
CREATE TABLE IF NOT EXISTS bid.bid_ai_run (
  id                   UUID NOT NULL DEFAULT gen_random_uuid(),   -- AI运行ID
  bid_id               UUID NOT NULL,   -- 标书ID
  task_id              UUID,   -- 正文生成任务ID，非正文调用可为空
  snapshot_id          UUID,   -- 生成快照ID，非正文调用可为空
  generation_unit_id   UUID,   -- 生成单元ID，非正文调用可为空
  operation_type       VARCHAR(48) NOT NULL,   -- 操作类型：INTERPRETATION, OUTLINE, CHAPTER_UNIT, REVISION, REVIEW
  provider             VARCHAR(32) NOT NULL,   -- AI提供方：DIRECT_QWEN, DIFY或UNKNOWN
  model_name           VARCHAR(100) NOT NULL,   -- 模型名称，不包含凭据
  prompt_version       VARCHAR(64) NOT NULL,   -- 提示词契约版本
  input_snapshot_hash  CHAR(64) NOT NULL,   -- 规范化模型输入SHA-256
  idempotency_key      VARCHAR(255) NOT NULL,   -- 业务幂等键，不包含密钥或正文
  status               VARCHAR(24) NOT NULL,   -- 状态：RUNNING, SUCCEEDED, FAILED
  duration_ms          BIGINT,   -- 调用耗时，单位：毫秒
  input_tokens         BIGINT,   -- 模型报告的输入Token数
  output_tokens        BIGINT,   -- 模型报告的输出Token数
  output_hash          CHAR(64),   -- 结构化模型输出SHA-256
  error_code           VARCHAR(64),   -- 稳定错误码
  error_message        VARCHAR(1000),   -- 已过滤敏感信息的错误摘要
  started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 开始时间，时区UTC+8
  finished_at          TIMESTAMPTZ,   -- 结束时间，时区UTC+8
  object_name          VARCHAR(100),   -- 单次AI调用负责的业务对象名称
  schema_version       VARCHAR(64),   -- 输出Schema版本
  attempt_count        INTEGER NOT NULL DEFAULT 1,   -- 同一幂等AI运行的实际调用尝试次数
  failure_reason       VARCHAR(1000),   -- 字段级校验或上游失败原因，不含完整响应
  finish_reason        VARCHAR(32),   -- 模型finish_reason，例如stop或length
  response_length      INTEGER,   -- 模型原始响应字符数
  response_hash        CHAR(64),   -- 模型原始响应SHA-256，不保存原文
  response_fields      VARCHAR(1000),   -- 成功响应的顶层字段名，逗号分隔
  reasoning_tokens     BIGINT,   -- 模型报告的思考Token数，不包含最终答案Token
  cached_input_tokens  BIGINT,   -- 模型报告的命中缓存输入Token数
  model_attempt_count  INTEGER NOT NULL DEFAULT 1,   -- 本次业务运行内部的模型请求或结构修复次数
  current_attempt_id   UUID,   -- 当前逻辑运行尝试ID，用于完成或失败时精确落账
  CONSTRAINT pk_bid_ai_run PRIMARY KEY (id),
  CONSTRAINT uidx_bid_ai_run_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_bid_ai_run_trace ON bid.bid_ai_run (bid_id, started_at, operation_type);

-- 每次真实业务尝试的AI元数据；重试不覆盖历史Token和耗时
CREATE TABLE IF NOT EXISTS bid.bid_ai_run_attempt (
  id                   UUID NOT NULL DEFAULT gen_random_uuid(),   -- AI运行尝试ID
  ai_run_id            UUID NOT NULL,   -- 逻辑AI运行ID
  attempt_no           INTEGER NOT NULL,   -- 同一幂等运行的业务尝试序号，从1开始
  status               VARCHAR(24) NOT NULL,   -- RUNNING、SUCCEEDED、FAILED或INTERRUPTED
  duration_ms          BIGINT,   -- 该次尝试耗时，单位毫秒
  input_tokens         BIGINT,   -- 该次尝试输入Token
  output_tokens        BIGINT,   -- 该次尝试输出Token
  reasoning_tokens     BIGINT,   -- 该次尝试思考Token
  cached_input_tokens  BIGINT,   -- 该次尝试命中缓存的输入Token
  model_attempt_count  INTEGER NOT NULL DEFAULT 1,   -- 提供方重试及结构修复请求数
  finish_reason        VARCHAR(32),   -- 模型finish_reason
  response_length      INTEGER,   -- 模型原始响应字符数
  response_hash        CHAR(64),   -- 模型原始响应SHA-256
  error_code           VARCHAR(64),   -- 稳定错误码
  error_message        VARCHAR(1000),   -- 过滤后的失败摘要
  failure_reason       VARCHAR(1000),   -- 过滤后的字段级诊断
  started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 开始时间
  finished_at          TIMESTAMPTZ,   -- 结束时间
  CONSTRAINT pk_bid_ai_run_attempt PRIMARY KEY (id),
  CONSTRAINT uidx_bid_ai_run_attempt UNIQUE (ai_run_id, attempt_no)
);
CREATE INDEX IF NOT EXISTS idx_bid_ai_run_attempt_trace ON bid.bid_ai_run_attempt (ai_run_id, attempt_no, status);

CREATE TABLE IF NOT EXISTS bid.bid_reference_asset (
  id                  UUID NOT NULL DEFAULT gen_random_uuid(),
  owner_id            VARCHAR(255) NOT NULL,   -- 归属人；平台 subject 或本地用户 id
  org_id              VARCHAR(64) NOT NULL,   -- 平台组织标识；local: 前缀为过渡值
  workspace_id        VARCHAR(64) NOT NULL,   -- 平台工作空间标识，租户隔离键；local: 前缀为过渡值
  category            VARCHAR(24) NOT NULL,
  display_name        VARCHAR(160) NOT NULL,
  original_file_name  VARCHAR(255) NOT NULL,
  object_key          VARCHAR(500) NOT NULL,
  media_type          VARCHAR(160) NOT NULL,
  file_size           BIGINT NOT NULL,
  content_hash        VARCHAR(64) NOT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  revision            BIGINT NOT NULL DEFAULT 0,
  ingestion_error     VARCHAR(1000),   -- 素材解析失败时的已过滤错误摘要
  CONSTRAINT pk_bid_reference_asset PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_asset_owner ON bid.bid_reference_asset (owner_id, category, status, updated_at);
CREATE INDEX IF NOT EXISTS idx_bid_reference_asset_workspace ON bid.bid_reference_asset (workspace_id, created_at);

-- 标书范本和大纲一次解析后形成的稳定文本分块
CREATE TABLE IF NOT EXISTS bid.bid_asset_chunk (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),   -- 素材文本分块ID
  asset_id         UUID NOT NULL,   -- 参考素材ID，关联bid_reference_asset.id
  chunk_index      INTEGER NOT NULL,   -- 素材内稳定分块序号，从0开始
  heading          VARCHAR(500) NOT NULL,   -- 分块所属标题或定位摘要
  source_locator   VARCHAR(255) NOT NULL,   -- 原文件页码、段落或表格定位
  content          TEXT NOT NULL,   -- 可检索素材文本
  content_hash     CHAR(64) NOT NULL,   -- 分块文本SHA-256
  character_count  INTEGER NOT NULL,   -- 分块字符数，单位：字符
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  CONSTRAINT pk_bid_asset_chunk PRIMARY KEY (id),
  CONSTRAINT uidx_bid_asset_chunk UNIQUE (asset_id, chunk_index)
);
CREATE INDEX IF NOT EXISTS idx_bid_asset_chunk_lookup ON bid.bid_asset_chunk (asset_id, chunk_index);

CREATE TABLE IF NOT EXISTS bid.bid_asset_selection (
  bid_id       UUID NOT NULL,
  asset_id     UUID NOT NULL,
  selected_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT pk_bid_asset_selection PRIMARY KEY (bid_id, asset_id)
);

CREATE TABLE IF NOT EXISTS bid.bid_outline_node (
  id                      UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id                  UUID NOT NULL,
  parent_id               UUID,
  level_no                INTEGER NOT NULL,
  title                   VARCHAR(200) NOT NULL,
  planned_pages           INTEGER NOT NULL DEFAULT 0,
  sort_order              INTEGER NOT NULL,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  revision                BIGINT NOT NULL DEFAULT 0,
  task_brief              TEXT,
  must_keywords_json      TEXT,
  scoring_point_ids_json  TEXT,
  CONSTRAINT pk_bid_outline_node PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_outline_order ON bid.bid_outline_node (bid_id, sort_order);

CREATE TABLE IF NOT EXISTS bid.bid_chapter (
  id                 UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id             UUID NOT NULL,
  outline_node_id    UUID NOT NULL,
  title              VARCHAR(200) NOT NULL,
  content            TEXT NOT NULL,
  generation_status  VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  revision           BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT pk_bid_chapter PRIMARY KEY (id),
  CONSTRAINT uidx_bid_chapter_outline UNIQUE (outline_node_id)
);
CREATE INDEX IF NOT EXISTS idx_bid_chapter_bid ON bid.bid_chapter (bid_id, updated_at);

-- 正文生成的最小可重试写作单元，当前首期每个叶子章节一个单元
CREATE TABLE IF NOT EXISTS bid.bid_generation_unit (
  id                     UUID NOT NULL DEFAULT gen_random_uuid(),   -- 正文生成单元ID
  task_id                UUID NOT NULL,   -- 生成任务ID
  bid_id                 UUID NOT NULL,   -- 标书ID
  chapter_id             UUID NOT NULL,   -- 叶子章节ID
  unit_index             INTEGER NOT NULL,   -- 章节内单元序号，从0开始
  status                 VARCHAR(24) NOT NULL,   -- 状态：PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED
  attempt_count          INTEGER NOT NULL DEFAULT 0,   -- 已经执行的尝试次数
  word_budget            INTEGER NOT NULL,   -- 写作字数预算，单位：中文字符
  visible_characters     INTEGER,   -- 模型单元正文的可见字符数，不含HTML标签和空白字符，单位：字符
  budget_variance_ratio  NUMERIC(8,4),   -- 可见字符相对word_budget的偏差比例，(实际-预算)/预算，可为负数
  budget_status          VARCHAR(24) NOT NULL DEFAULT 'PENDING',   -- 软预算状态：PENDING、WITHIN_BUDGET、OVER_BUDGET或COMPACTED
  compacted_at           TIMESTAMPTZ,   -- 所属章节因全文篇幅结算被接受压缩的时间，时区UTC+8
  summary                TEXT,   -- 生成完成后的上下文压缩摘要
  error_message          VARCHAR(1000),   -- 最后一次失败原因
  started_at             TIMESTAMPTZ,   -- 开始时间，时区UTC+8
  finished_at            TIMESTAMPTZ,   -- 结束时间，时区UTC+8
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 更新时间，时区UTC+8
  unit_title             VARCHAR(300) NOT NULL DEFAULT '',   -- 章节内分段标题或写作范围
  idempotency_key        VARCHAR(255),   -- 单元模型调用和持久化的稳定幂等键
  content                TEXT,   -- 该分段已生成的受限HTML正文
  content_hash           CHAR(64),   -- 分段正文SHA-256
  ai_run_id              UUID,   -- 最近一次AI运行ID
  previous_summary       TEXT,   -- 生成该分段时使用的前文压缩摘要
  CONSTRAINT pk_bid_generation_unit PRIMARY KEY (id),
  CONSTRAINT uidx_bid_generation_unit UNIQUE (task_id, chapter_id, unit_index),
  CONSTRAINT uidx_bid_generation_unit_key UNIQUE (idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_bid_unit_status ON bid.bid_generation_unit (task_id, status, unit_index);
CREATE INDEX IF NOT EXISTS idx_bid_generation_unit_budget ON bid.bid_generation_unit (task_id, budget_status, chapter_id);

-- 章节正文不可变版本，当前bid_chapter保存活动版本以兼容编辑器
CREATE TABLE IF NOT EXISTS bid.bid_chapter_version (
  id                  UUID NOT NULL DEFAULT gen_random_uuid(),   -- 章节版本ID
  bid_id              UUID NOT NULL,   -- 标书ID
  chapter_id          UUID NOT NULL,   -- 章节ID
  version_no          INTEGER NOT NULL,   -- 章节内递增版本号
  source_type         VARCHAR(24) NOT NULL,   -- 来源：AI, MANUAL, AI_REVISION
  content             TEXT NOT NULL,   -- 兼容Tiptap的受限HTML正文
  content_hash        CHAR(64) NOT NULL,   -- 正文规范化SHA-256
  summary             TEXT,   -- 供后续章节保持连贯的摘要
  created_by          VARCHAR(255),   -- 创建者；平台 subject 或本地用户 id
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  generation_unit_id  UUID,   -- 产生该版本的生成单元ID，人工版本为空
  provider            VARCHAR(32),   -- AI版本使用的提供方，人工版本为空
  model_name          VARCHAR(100),   -- AI版本使用的模型，人工版本为空
  prompt_version      VARCHAR(64),   -- AI版本使用的提示词契约版本，人工版本为空
  CONSTRAINT pk_bid_chapter_version PRIMARY KEY (id),
  CONSTRAINT uidx_bid_chapter_version UNIQUE (chapter_id, version_no),
  CONSTRAINT uidx_bid_chapter_version_unit UNIQUE (generation_unit_id)
);
CREATE INDEX IF NOT EXISTS idx_bid_chapter_version_latest ON bid.bid_chapter_version (chapter_id, version_no);

-- 正式DOCX生产和质量检查任务
CREATE TABLE IF NOT EXISTS bid.bid_layout_job (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),   -- 排版任务ID
  bid_id           UUID NOT NULL,   -- 标书ID
  status           VARCHAR(24) NOT NULL,   -- 状态：PENDING, RUNNING, SUCCEEDED, FAILED
  workflow_run_id  VARCHAR(160),   -- Temporal工作流运行ID；本地模式记录local前缀ID
  input_hash       CHAR(64) NOT NULL,   -- 冻结正文与排版配置SHA-256
  target_pages     INTEGER NOT NULL,   -- 目标页数，单位：页
  actual_pages     INTEGER,   -- QA渲染得到的实际页数，单位：页
  qa_status        VARCHAR(24) NOT NULL DEFAULT 'PENDING',   -- QA状态：PENDING, PASSED, FAILED
  qa_summary       VARCHAR(2000),   -- 页数偏差、空白页和元数据检查摘要
  error_message    VARCHAR(1000),   -- 排版失败原因
  created_by       VARCHAR(255),   -- 创建者；平台 subject 或本地用户 id
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  finished_at      TIMESTAMPTZ,   -- 完成时间，时区UTC+8
  CONSTRAINT pk_bid_layout_job PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_layout_latest ON bid.bid_layout_job (bid_id, created_at);

CREATE TABLE IF NOT EXISTS bid.bid_export (
  id             UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id         UUID NOT NULL,
  version_no     INTEGER NOT NULL,
  file_name      VARCHAR(255) NOT NULL,
  object_key     VARCHAR(500) NOT NULL,
  file_size      BIGINT NOT NULL,
  created_by     VARCHAR(255),   -- 创建者；平台 subject 或本地用户 id
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  layout_job_id  UUID,   -- 产生该成果的排版任务ID
  qa_status      VARCHAR(24) NOT NULL DEFAULT 'NOT_CHECKED',   -- 成果QA状态：NOT_CHECKED, PASSED, FAILED
  CONSTRAINT pk_bid_export PRIMARY KEY (id),
  CONSTRAINT uidx_bid_export_version UNIQUE (bid_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_bid_export_latest ON bid.bid_export (bid_id, version_no);

-- 招标文件技术解读不可变版本，随人工保存或冻结创建
CREATE TABLE IF NOT EXISTS bid.bid_interpretation_version (
  id            UUID NOT NULL DEFAULT gen_random_uuid(),   -- 解读版本ID
  bid_id        UUID NOT NULL,   -- 标书ID，关联bid_document.id
  version_no    INTEGER NOT NULL,   -- 标书内递增版本号
  status        VARCHAR(24) NOT NULL,   -- 版本状态：REVIEW或FROZEN
  content_hash  CHAR(64) NOT NULL,   -- 规范化解读内容SHA-256
  created_by    VARCHAR(255),   -- 创建者；平台 subject 或本地用户 id
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  frozen_at     TIMESTAMPTZ,   -- 冻结时间，时区UTC+8
  CONSTRAINT pk_bid_interpretation_version PRIMARY KEY (id),
  CONSTRAINT uidx_bid_interpretation_version UNIQUE (bid_id, version_no)
);

-- 经人工确认的指标、术语和固定事实字典
CREATE TABLE IF NOT EXISTS bid.bid_frozen_fact (
  id                         UUID NOT NULL DEFAULT gen_random_uuid(),   -- 冻结事实ID
  bid_id                     UUID NOT NULL,   -- 标书ID
  interpretation_version_id  UUID NOT NULL,   -- 来源解读版本ID
  fact_type                  VARCHAR(24) NOT NULL,   -- 类型：METRIC, TERM, FIXED_FACT
  fact_name                  VARCHAR(200) NOT NULL,   -- 指标、术语或事实名称
  fact_value                 VARCHAR(2000) NOT NULL,   -- 后续生成必须遵守的唯一口径
  source_locator             VARCHAR(255) NOT NULL,   -- 原文定位或人工冲突处理定位
  forbidden_values           VARCHAR(2000),   -- 禁止出现的冲突值，多个值使用换行分隔
  sort_order                 INTEGER NOT NULL,   -- 展示及提示词顺序，从0开始
  created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 冻结时间，时区UTC+8
  CONSTRAINT pk_bid_frozen_fact PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_frozen_fact_bid ON bid.bid_frozen_fact (bid_id, sort_order);

-- 正文生成任务的可查询事件流，不存储正文和模型密钥
CREATE TABLE IF NOT EXISTS bid.bid_generation_event (
  id           UUID NOT NULL DEFAULT gen_random_uuid(),   -- 生成事件ID
  task_id      UUID NOT NULL,   -- 生成任务ID
  bid_id       UUID NOT NULL,   -- 标书ID
  chapter_id   UUID,   -- 关联章节ID，可为空
  event_type   VARCHAR(48) NOT NULL,   -- 事件类型，例如TASK_STARTED或CHAPTER_SUCCEEDED
  message      VARCHAR(1000) NOT NULL,   -- 面向用户的事件摘要
  occurred_at  TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 事件时间，时区UTC+8
  CONSTRAINT pk_bid_generation_event PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_event_order ON bid.bid_generation_event (bid_id, occurred_at, id);

-- 目录重新生成前的可恢复业务快照
CREATE TABLE IF NOT EXISTS bid.bid_outline_regeneration_archive (
  id                      UUID NOT NULL DEFAULT gen_random_uuid(),   -- 目录重新生成恢复快照ID
  bid_id                  UUID NOT NULL,   -- 标书ID
  source_outline_version  INTEGER NOT NULL,   -- 重新生成前的目录版本号
  outline_json            TEXT NOT NULL,   -- 旧活动目录节点JSON
  chapter_json            TEXT NOT NULL,   -- 旧活动正文JSON
  chapter_version_json    TEXT NOT NULL,   -- 旧正文版本JSON
  generation_unit_json    TEXT NOT NULL,   -- 旧正文生成单元JSON
  review_issue_json       TEXT NOT NULL,   -- 旧正文审查问题JSON
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 归档时间
  CONSTRAINT pk_bid_outline_regeneration_archive PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_outline_archive_bid ON bid.bid_outline_regeneration_archive (bid_id, created_at);

CREATE TABLE IF NOT EXISTS bid.bid_outline_task (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id           UUID NOT NULL,
  status           VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  stage            VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
  progress         INTEGER NOT NULL DEFAULT 0,
  input_revision   BIGINT NOT NULL,
  workflow_run_id  VARCHAR(128),
  error_message    VARCHAR(1000),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  started_at       TIMESTAMPTZ,
  finished_at      TIMESTAMPTZ,
  CONSTRAINT pk_bid_outline_task PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_outline_task_latest ON bid.bid_outline_task (bid_id, created_at);

-- 目录生成阶段结果；由Worker按阶段实时写入，随目录任务保留，用于重试恢复和耗时诊断
CREATE TABLE IF NOT EXISTS bid.bid_outline_stage_result (
  id              UUID NOT NULL DEFAULT gen_random_uuid(),   -- 阶段结果ID，UUID
  task_id         UUID NOT NULL,   -- 目录任务ID，关联 bid_outline_task.id
  stage_type      VARCHAR(32) NOT NULL,   -- 阶段：STRATEGY、SKELETON、EXPANSION
  batch_index     INTEGER NOT NULL DEFAULT 0,   -- 批次序号，策略和骨架固定为0，扩展从1开始
  status          VARCHAR(24) NOT NULL,   -- 状态：RUNNING、SUCCEEDED、FAILED
  input_hash      CHAR(64) NOT NULL,   -- 严格类型输入的SHA-256，用于幂等恢复
  output_payload  TEXT,   -- 通过Java/Pydantic契约校验的阶段结果JSON，包含项目派生内容，敏感
  output_hash     CHAR(64),   -- 阶段结果SHA-256，不包含模型凭据
  model_name      VARCHAR(100) NOT NULL,   -- 该阶段实际使用的模型名称，不包含凭据
  duration_ms     BIGINT,   -- 最近一次执行耗时，单位：毫秒
  attempt_count   INTEGER NOT NULL DEFAULT 1,   -- 该阶段开始执行的累计次数
  error_code      VARCHAR(64),   -- 稳定错误码，不包含堆栈和凭据
  error_message   VARCHAR(1000),   -- 过滤后的失败摘要，不保存提示词或模型原文
  started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 最近一次开始时间，时区：数据库会话时区
  finished_at     TIMESTAMPTZ,   -- 最近一次完成时间，时区：数据库会话时区
  CONSTRAINT pk_bid_outline_stage_result PRIMARY KEY (id),
  CONSTRAINT uidx_bid_outline_stage UNIQUE (task_id, stage_type, batch_index)
);
CREATE INDEX IF NOT EXISTS idx_bid_outline_stage_status ON bid.bid_outline_stage_result (task_id, status, stage_type, batch_index);

-- 模型或规则发现的招标文件数值与口径冲突，必须人工处理
CREATE TABLE IF NOT EXISTS bid.bid_requirement_conflict (
  id            UUID NOT NULL DEFAULT gen_random_uuid(),   -- 冲突项ID
  bid_id        UUID NOT NULL,   -- 标书ID
  conflict_key  VARCHAR(200) NOT NULL,   -- 冲突主题，例如前置代理服务器内存
  left_value    VARCHAR(1000) NOT NULL,   -- 冲突值A
  left_source   VARCHAR(255) NOT NULL,   -- 冲突值A原文定位
  right_value   VARCHAR(1000) NOT NULL,   -- 冲突值B
  right_source  VARCHAR(255) NOT NULL,   -- 冲突值B原文定位
  status        VARCHAR(24) NOT NULL DEFAULT 'OPEN',   -- 状态：OPEN或RESOLVED
  resolution    VARCHAR(2000),   -- 人工冻结的唯一口径
  resolved_by   VARCHAR(255),   -- 处理人；平台 subject 或本地用户 id
  resolved_at   TIMESTAMPTZ,   -- 处理时间，时区UTC+8
  revision      BIGINT NOT NULL DEFAULT 0,   -- 乐观锁版本
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  CONSTRAINT pk_bid_requirement_conflict PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_conflict_status ON bid.bid_requirement_conflict (bid_id, status, created_at);

-- 解读版本内的评分点、废标条款、格式要求和事实快照
CREATE TABLE IF NOT EXISTS bid.bid_requirement_item (
  id                         UUID NOT NULL DEFAULT gen_random_uuid(),   -- 版本要求项ID
  interpretation_version_id  UUID NOT NULL,   -- 解读版本ID
  source_criterion_id        UUID NOT NULL,   -- 当前工作区要求项ID
  item_type                  VARCHAR(24) NOT NULL,   -- 类型：SCORING, REJECTION, FORMAT, FACT
  title                      VARCHAR(200) NOT NULL,   -- 要求标题
  description                TEXT NOT NULL,   -- 要求完整描述
  score                      NUMERIC(10,2),   -- 评分分值，单位：分
  source_locator             VARCHAR(255) NOT NULL,   -- 原文页码、段落或表格定位
  source_excerpt             TEXT,   -- 原文证据摘录
  scope                      VARCHAR(24) NOT NULL,   -- 范围：TECHNICAL, COMMERCIAL, MIXED, FORMAT
  confidence                 VARCHAR(16) NOT NULL,   -- 置信度：HIGH, MEDIUM, LOW
  sort_order                 INTEGER NOT NULL,   -- 版本内展示顺序，从0开始
  CONSTRAINT pk_bid_requirement_item PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_requirement_version ON bid.bid_requirement_item (interpretation_version_id, sort_order);

-- 正文与排版审查问题，阻断级问题未解决时不得冻结成稿
CREATE TABLE IF NOT EXISTS bid.bid_review_issue (
  id           UUID NOT NULL DEFAULT gen_random_uuid(),   -- 审查问题ID
  bid_id       UUID NOT NULL,   -- 标书ID
  chapter_id   UUID,   -- 关联章节ID，全局问题可为空
  severity     VARCHAR(16) NOT NULL,   -- 严重度：ERROR或WARNING
  issue_code   VARCHAR(64) NOT NULL,   -- 稳定问题编码
  message      VARCHAR(1000) NOT NULL,   -- 问题描述
  suggestion   VARCHAR(2000) NOT NULL,   -- 可执行整改建议
  status       VARCHAR(24) NOT NULL DEFAULT 'OPEN',   -- 状态：OPEN, RESOLVED, IGNORED
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 发现时间，时区UTC+8
  resolved_at  TIMESTAMPTZ,   -- 处理时间，时区UTC+8
  CONSTRAINT pk_bid_review_issue PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_review_status ON bid.bid_review_issue (bid_id, status, severity);

CREATE TABLE IF NOT EXISTS bid.bid_scoring_criterion (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id           UUID NOT NULL,
  item_type        VARCHAR(24) NOT NULL,
  title            VARCHAR(200) NOT NULL,
  description      TEXT NOT NULL,
  score            NUMERIC(10,2),
  source_excerpt   TEXT,
  sort_order       INTEGER NOT NULL,
  manually_edited  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  source_locator   VARCHAR(255) NOT NULL DEFAULT '',
  scope            VARCHAR(24) NOT NULL DEFAULT 'TECHNICAL',
  confidence       VARCHAR(16) NOT NULL DEFAULT 'HIGH',
  CONSTRAINT pk_bid_scoring_criterion PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_criterion_order ON bid.bid_scoring_criterion (bid_id, item_type, sort_order);

-- 生成快照选用的参考素材分块副本，素材删除后仍可复现
CREATE TABLE IF NOT EXISTS bid.bid_snapshot_asset_chunk (
  id               UUID NOT NULL DEFAULT gen_random_uuid(),   -- 快照素材分块ID
  snapshot_id      UUID NOT NULL,   -- 生成快照ID
  source_chunk_id  UUID NOT NULL,   -- 创建快照时的素材分块ID
  asset_id         UUID NOT NULL,   -- 参考素材ID
  asset_category   VARCHAR(24) NOT NULL,   -- 素材类别：TEMPLATE或OUTLINE
  asset_name       VARCHAR(160) NOT NULL,   -- 素材显示名称
  heading          VARCHAR(500) NOT NULL,   -- 分块标题或定位摘要
  source_locator   VARCHAR(255) NOT NULL,   -- 原素材定位
  content          TEXT NOT NULL,   -- 快照内不可变素材文本
  content_hash     CHAR(64) NOT NULL,   -- 素材分块文本SHA-256
  chunk_index      INTEGER NOT NULL,   -- 素材内分块序号，从0开始
  CONSTRAINT pk_bid_snapshot_asset_chunk PRIMARY KEY (id),
  CONSTRAINT uidx_bid_snapshot_asset_chunk UNIQUE (snapshot_id, source_chunk_id)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_asset_order ON bid.bid_snapshot_asset_chunk (snapshot_id, asset_id, chunk_index);

-- 正文生成复用的二级技术域蓝图；模型成功后只写一次，重试直接复用
CREATE TABLE IF NOT EXISTS bid.bid_snapshot_branch_blueprint (
  id                 UUID NOT NULL DEFAULT gen_random_uuid(),   -- 二级技术域蓝图ID
  snapshot_id        UUID NOT NULL,   -- 不可变正文快照ID
  branch_outline_id  UUID NOT NULL,   -- 快照内二级目录的source_outline_id
  input_hash         CHAR(64) NOT NULL,   -- 蓝图严格输入SHA-256
  blueprint_payload  TEXT NOT NULL,   -- 通过严格契约校验的二级技术域蓝图JSON
  output_hash        CHAR(64) NOT NULL,   -- 蓝图结构化结果SHA-256
  prompt_version     VARCHAR(64) NOT NULL,   -- 蓝图提示词版本
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间
  CONSTRAINT pk_bid_snapshot_branch_blueprint PRIMARY KEY (id),
  CONSTRAINT uidx_bid_snapshot_branch_blueprint UNIQUE (snapshot_id, branch_outline_id, input_hash)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_branch_blueprint_lookup ON bid.bid_snapshot_branch_blueprint (snapshot_id, branch_outline_id);

-- 生成快照中的不可变指标、术语和固定事实
CREATE TABLE IF NOT EXISTS bid.bid_snapshot_fact (
  id                UUID NOT NULL DEFAULT gen_random_uuid(),   -- 快照冻结事实ID
  snapshot_id       UUID NOT NULL,   -- 生成快照ID
  fact_type         VARCHAR(24) NOT NULL,   -- 类型：METRIC, TERM, FIXED_FACT
  fact_name         VARCHAR(200) NOT NULL,   -- 指标、术语或事实名称
  fact_value        VARCHAR(2000) NOT NULL,   -- 生成必须遵守的唯一口径
  source_locator    VARCHAR(255) NOT NULL,   -- 原文或人工冲突处理定位
  forbidden_values  TEXT NOT NULL,   -- 禁止出现的冲突值，换行分隔
  sort_order        INTEGER NOT NULL,   -- 快照内顺序，从0开始
  CONSTRAINT pk_bid_snapshot_fact PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_fact_order ON bid.bid_snapshot_fact (snapshot_id, sort_order);

-- 生成快照中的冻结三级目录和章节契约副本
CREATE TABLE IF NOT EXISTS bid.bid_snapshot_outline (
  id                         UUID NOT NULL DEFAULT gen_random_uuid(),   -- 快照目录节点ID
  snapshot_id                UUID NOT NULL,   -- 生成快照ID
  source_outline_id          UUID NOT NULL,   -- 创建快照时的工作区目录节点ID
  chapter_id                 UUID,   -- 叶子目录对应的活动章节ID，非叶子为空
  chapter_generation_status  VARCHAR(24),   -- 快照创建时章节状态，叶子目录填写
  parent_source_outline_id   UUID,   -- 父目录的工作区节点ID，一级目录为空
  level_no                   INTEGER NOT NULL,   -- 目录层级：1至3
  title                      VARCHAR(200) NOT NULL,   -- 目录标题
  planned_pages              INTEGER NOT NULL,   -- 叶子章节计划页数，单位：页
  sort_order                 INTEGER NOT NULL,   -- 全文顺序，从0开始
  task_brief                 TEXT NOT NULL,   -- 章节写作任务说明
  must_keywords              TEXT NOT NULL,   -- 章节必含关键词，换行分隔
  scoring_point_ids          TEXT NOT NULL,   -- 覆盖评分点ID，换行分隔
  CONSTRAINT pk_bid_snapshot_outline PRIMARY KEY (id),
  CONSTRAINT uidx_bid_snapshot_outline UNIQUE (snapshot_id, source_outline_id)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_outline_order ON bid.bid_snapshot_outline (snapshot_id, sort_order);

-- 生成快照中的评分点、废标条款、格式要求和事实副本
CREATE TABLE IF NOT EXISTS bid.bid_snapshot_requirement (
  id                   UUID NOT NULL DEFAULT gen_random_uuid(),   -- 快照需求项ID
  snapshot_id          UUID NOT NULL,   -- 生成快照ID
  source_criterion_id  UUID NOT NULL,   -- 创建快照时的工作区需求项ID
  item_type            VARCHAR(24) NOT NULL,   -- 类型：SCORING, REJECTION, FORMAT, FACT
  title                VARCHAR(200) NOT NULL,   -- 需求标题
  description          TEXT NOT NULL,   -- 需求完整描述
  score                NUMERIC(10,2),   -- 评分分值，单位：分
  source_excerpt       TEXT,   -- 招标文件证据摘录
  source_locator       VARCHAR(255) NOT NULL,   -- 证据页码、段落或表格定位
  sort_order           INTEGER NOT NULL,   -- 快照内顺序，从0开始
  CONSTRAINT pk_bid_snapshot_requirement PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_bid_snapshot_requirement_order ON bid.bid_snapshot_requirement (snapshot_id, sort_order);

CREATE TABLE IF NOT EXISTS bid.bid_source_file (
  id                      UUID NOT NULL DEFAULT gen_random_uuid(),
  bid_id                  UUID NOT NULL,
  original_file_name      VARCHAR(255) NOT NULL,
  object_key              VARCHAR(500) NOT NULL,
  media_type              VARCHAR(160) NOT NULL,
  file_size               BIGINT NOT NULL,
  content_hash            VARCHAR(64) NOT NULL,
  parse_status            VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  extracted_text          TEXT,
  error_message           VARCHAR(1000),
  uploaded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  parse_stage             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  parse_progress          INTEGER NOT NULL DEFAULT 0,
  parse_started_at        TIMESTAMPTZ,
  parse_finished_at       TIMESTAMPTZ,
  overview_status         VARCHAR(24) NOT NULL DEFAULT 'PENDING',   -- 项目概述状态：PENDING、RUNNING、SUCCEEDED、FAILED
  overview_content        TEXT,   -- 最近一次成功生成的项目概述Markdown
  overview_error_message  VARCHAR(1000),   -- 项目概述最近失败的安全错误摘要
  overview_completed_at   TIMESTAMPTZ,   -- 项目概述完成时间，时区UTC+8
  scoring_status          VARCHAR(24) NOT NULL DEFAULT 'PENDING',   -- 技术评分状态：PENDING、RUNNING、SUCCEEDED、FAILED
  scoring_content         TEXT,   -- 最近一次成功生成的技术评分要求Markdown
  scoring_error_message   VARCHAR(1000),   -- 技术评分最近失败的安全错误摘要
  scoring_completed_at    TIMESTAMPTZ,   -- 技术评分完成时间，时区UTC+8
  CONSTRAINT pk_bid_source_file PRIMARY KEY (id),
  CONSTRAINT uidx_bid_source_current UNIQUE (bid_id)
);

-- 招标文件有序解析片段，文件只解析一次，对象级AI重试直接复用
CREATE TABLE IF NOT EXISTS bid.bid_source_segment (
  id            BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,   -- 源片段主键ID
  source_id     UUID NOT NULL,   -- 招标文件ID，关联bid_source_file.id
  bid_id        UUID NOT NULL,   -- 标书ID，关联bid_document.id
  sequence_no   INTEGER NOT NULL,   -- 片段在解析结果中的顺序，从0开始
  locator_type  VARCHAR(32) NOT NULL,   -- 定位类型：PARAGRAPH、TABLE、PAGE等
  locator       VARCHAR(300) NOT NULL,   -- 来源位置，例如段落号、页码或表格位置
  segment_text  TEXT NOT NULL,   -- 解析出的文字或表格文本，仅用于本标书AI处理
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),   -- 创建时间，时区UTC+8
  CONSTRAINT pk_bid_source_segment PRIMARY KEY (id),
  CONSTRAINT uidx_bid_source_segment_order UNIQUE (source_id, sequence_no)
);
CREATE INDEX IF NOT EXISTS idx_bid_source_segment_bid ON bid.bid_source_segment (bid_id, source_id, sequence_no);

-- ==========================================================================
-- 引用完整性
--
-- 全部外键集中在这里，而不是写进各自的 CREATE TABLE：本仓的引用关系存在环
--（bid_ai_run 与 bid_generation_snapshot 互指），任何按依赖排序建表的方案都会
-- 在环上断掉，而断掉的表现是 psql 报「relation does not exist」——那时已经建了
-- 一半。集中在末尾，顺序问题从根上不存在。
-- ==========================================================================
ALTER TABLE bid.bid_generation_task ADD CONSTRAINT fk_bid_task_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_generation_task ADD CONSTRAINT fk_bid_task_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_generation_snapshot ADD CONSTRAINT fk_bid_snapshot_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_generation_snapshot ADD CONSTRAINT fk_bid_snapshot_task
  FOREIGN KEY (task_id) REFERENCES bid.bid_generation_task (id);
ALTER TABLE bid.bid_ai_run ADD CONSTRAINT fk_bid_ai_run_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_ai_run ADD CONSTRAINT fk_bid_ai_run_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_ai_run ADD CONSTRAINT fk_bid_ai_run_task
  FOREIGN KEY (task_id) REFERENCES bid.bid_generation_task (id);
ALTER TABLE bid.bid_ai_run_attempt ADD CONSTRAINT fk_bid_ai_run_attempt_run
  FOREIGN KEY (ai_run_id) REFERENCES bid.bid_ai_run (id);
ALTER TABLE bid.bid_asset_chunk ADD CONSTRAINT fk_bid_asset_chunk_asset
  FOREIGN KEY (asset_id) REFERENCES bid.bid_reference_asset (id);
ALTER TABLE bid.bid_asset_selection ADD CONSTRAINT fk_bid_selection_asset
  FOREIGN KEY (asset_id) REFERENCES bid.bid_reference_asset (id);
ALTER TABLE bid.bid_asset_selection ADD CONSTRAINT fk_bid_selection_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_outline_node ADD CONSTRAINT fk_bid_outline_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_outline_node ADD CONSTRAINT fk_bid_outline_parent
  FOREIGN KEY (parent_id) REFERENCES bid.bid_outline_node (id);
ALTER TABLE bid.bid_chapter ADD CONSTRAINT fk_bid_chapter_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_chapter ADD CONSTRAINT fk_bid_chapter_outline
  FOREIGN KEY (outline_node_id) REFERENCES bid.bid_outline_node (id);
ALTER TABLE bid.bid_generation_unit ADD CONSTRAINT fk_bid_generation_unit_ai_run
  FOREIGN KEY (ai_run_id) REFERENCES bid.bid_ai_run (id);
ALTER TABLE bid.bid_generation_unit ADD CONSTRAINT fk_bid_unit_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_generation_unit ADD CONSTRAINT fk_bid_unit_chapter
  FOREIGN KEY (chapter_id) REFERENCES bid.bid_chapter (id);
ALTER TABLE bid.bid_generation_unit ADD CONSTRAINT fk_bid_unit_task
  FOREIGN KEY (task_id) REFERENCES bid.bid_generation_task (id);
ALTER TABLE bid.bid_chapter_version ADD CONSTRAINT fk_bid_chapter_version_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_chapter_version ADD CONSTRAINT fk_bid_chapter_version_chapter
  FOREIGN KEY (chapter_id) REFERENCES bid.bid_chapter (id);
ALTER TABLE bid.bid_chapter_version ADD CONSTRAINT fk_bid_chapter_version_unit
  FOREIGN KEY (generation_unit_id) REFERENCES bid.bid_generation_unit (id);
ALTER TABLE bid.bid_layout_job ADD CONSTRAINT fk_bid_layout_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_export ADD CONSTRAINT fk_bid_export_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_export ADD CONSTRAINT fk_bid_export_layout
  FOREIGN KEY (layout_job_id) REFERENCES bid.bid_layout_job (id);
ALTER TABLE bid.bid_interpretation_version ADD CONSTRAINT fk_bid_interpretation_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_frozen_fact ADD CONSTRAINT fk_bid_frozen_fact_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_frozen_fact ADD CONSTRAINT fk_bid_frozen_fact_version
  FOREIGN KEY (interpretation_version_id) REFERENCES bid.bid_interpretation_version (id);
ALTER TABLE bid.bid_generation_event ADD CONSTRAINT fk_bid_event_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_generation_event ADD CONSTRAINT fk_bid_event_task
  FOREIGN KEY (task_id) REFERENCES bid.bid_generation_task (id);
ALTER TABLE bid.bid_outline_regeneration_archive ADD CONSTRAINT fk_bid_outline_archive_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_outline_task ADD CONSTRAINT fk_bid_outline_task_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_outline_stage_result ADD CONSTRAINT fk_bid_outline_stage_task
  FOREIGN KEY (task_id) REFERENCES bid.bid_outline_task (id) ON DELETE CASCADE;
ALTER TABLE bid.bid_requirement_conflict ADD CONSTRAINT fk_bid_conflict_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_requirement_item ADD CONSTRAINT fk_bid_requirement_version
  FOREIGN KEY (interpretation_version_id) REFERENCES bid.bid_interpretation_version (id);
ALTER TABLE bid.bid_review_issue ADD CONSTRAINT fk_bid_review_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_scoring_criterion ADD CONSTRAINT fk_bid_criterion_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_snapshot_asset_chunk ADD CONSTRAINT fk_bid_snapshot_asset_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_snapshot_branch_blueprint ADD CONSTRAINT fk_bid_snapshot_branch_blueprint_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_snapshot_fact ADD CONSTRAINT fk_bid_snapshot_fact_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_snapshot_outline ADD CONSTRAINT fk_bid_snapshot_outline_chapter
  FOREIGN KEY (chapter_id) REFERENCES bid.bid_chapter (id);
ALTER TABLE bid.bid_snapshot_outline ADD CONSTRAINT fk_bid_snapshot_outline_snapshot
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_snapshot_requirement ADD CONSTRAINT fk_bid_snapshot_requirement
  FOREIGN KEY (snapshot_id) REFERENCES bid.bid_generation_snapshot (id);
ALTER TABLE bid.bid_source_file ADD CONSTRAINT fk_bid_source_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_source_segment ADD CONSTRAINT fk_bid_source_segment_bid
  FOREIGN KEY (bid_id) REFERENCES bid.bid_document (id);
ALTER TABLE bid.bid_source_segment ADD CONSTRAINT fk_bid_source_segment_source
  FOREIGN KEY (source_id) REFERENCES bid.bid_source_file (id) ON DELETE CASCADE;
ALTER TABLE local_authz.user_session ADD CONSTRAINT fk_user_session_user
  FOREIGN KEY (user_id) REFERENCES local_authz.app_user (id);
