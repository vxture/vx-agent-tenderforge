-- GENERATED_BY_AI
-- MODEL: claude-opus-5
-- DATE: 2026-09-08
--
-- 平台接入 C1：OIDC 依赖方（RP）的两张表。
--
-- 【语法约束】沿用 V26：一条语句一列，不用 MySQL 专有的多列合并 ALTER 与
-- CHANGE COLUMN —— 生产是 MySQL 8.4，集成测试是 H2 的 MySQL 兼容模式，
-- 交集比 MySQL 窄。这条约束是被一次集成测试启动失败逼出来的，别退回去。
--
-- 【为什么不复用 user_session】
-- 那张表的 user_id 指向本地 app_user，而 IdP 用户<b>没有本地行</b>——
-- 身份来自平台，本地只保留业务角色。硬塞进去要么给每个登录用户建一个影子
-- app_user（凭空造出两份身份真源），要么把外键放开（那这张表就不再是它声称的东西）。
-- 分开还有一个好处：本地口令登录退役时，user_session 可以整张删掉。

-- ── 1. 授权请求暂存 ────────────────────────────────────────────────────────
--
-- state / nonce / PKCE verifier 必须<b>服务端持有</b>并与回调比对。
-- 放进 cookie 或塞进 state 本身（哪怕签名）都会把它暴露给浏览器，
-- 而 PKCE verifier 一旦可被读取，PKCE 就退化成了一个多余的往返。
--
-- 行是短命的：授权跳转到回调之间，正常在秒级。TTL 到期即视为无效，
-- 由回调路径顺手清理，不需要单独的定时任务。
CREATE TABLE oidc_authorization_request (
    state           VARCHAR(64)  NOT NULL COMMENT 'CSRF 防护随机串，同时是本行主键',
    nonce           VARCHAR(64)  NOT NULL COMMENT '重放防护随机串，必须与 id_token 的 nonce 一致',
    code_verifier   VARCHAR(128) NOT NULL COMMENT 'PKCE 验证串，绝不下发浏览器',
    return_to       VARCHAR(512) NULL     COMMENT '登录后回跳的站内路径，已白名单化',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at      TIMESTAMP    NOT NULL COMMENT '过期时间；过期的请求一律拒绝',
    PRIMARY KEY (state)
) COMMENT='OIDC 授权码流程的服务端暂存，一次性使用';

CREATE INDEX idx_oidc_authorization_request_expiry ON oidc_authorization_request (expires_at);

-- ── 2. RP 会话 ─────────────────────────────────────────────────────────────
--
-- 【浏览器零 token】token 留在这里，浏览器只拿一个不透明 cookie。
-- 存的是<b>哈希</b>而不是 cookie 值本身：库被读走时，读到的东西无法用来冒充任何人。
-- 这与 user_session 的做法一致，是这张表唯一沿用的东西。
--
-- access_token / refresh_token 是<b>凭证明文</b>。它们必须落库，因为静默续期和
-- S2S 换票都要用（OBO 模式拿用户 access_token 作 subject_token）。
-- 这就是「token 永不下发浏览器」的第二个理由——它是换票的原料。
CREATE TABLE rp_session (
    id                 VARCHAR(36)  NOT NULL COMMENT '会话标识',
    token_hash         VARCHAR(64)  NOT NULL COMMENT 'cookie 值的 SHA-256，cookie 原值不落库',
    subject            VARCHAR(255) NOT NULL COMMENT 'IdP 的 sub，平台内的用户标识',
    display_name       VARCHAR(255) NULL     COMMENT '展示名，取自 id_token 的 profile 声明；仅用于渲染',
    email              VARCHAR(255) NULL     COMMENT '展示用邮箱；不作为身份键',
    picture            VARCHAR(1024) NULL    COMMENT '头像地址；仅用于渲染',
    org_id             VARCHAR(64)  NULL     COMMENT 'access token 的 active_org',
    workspace_id       VARCHAR(64)  NULL     COMMENT 'access token 的 active_workspace',
    roles              VARCHAR(1024) NULL    COMMENT '治理角色，逗号分隔；带 scope 前缀如 workspace:owner',
    access_token       TEXT         NOT NULL COMMENT '平台 access token；换票与调用的原料，绝不下发浏览器',
    refresh_token      TEXT         NULL     COMMENT '刷新令牌；轮换时存新弃旧',
    access_expires_at  TIMESTAMP    NOT NULL COMMENT 'access token 过期时间，静默续期据此提前触发',
    expires_at         TIMESTAMP    NOT NULL COMMENT '会话过期时间',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_seen_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次使用',
    PRIMARY KEY (id)
) COMMENT='OIDC 依赖方会话；浏览器只持有不透明 cookie';

CREATE UNIQUE INDEX uk_rp_session_token ON rp_session (token_hash);
-- 反向登出按 sub 撤销该用户的全部会话，所以这一列要能被索引命中。
CREATE INDEX idx_rp_session_subject ON rp_session (subject);
CREATE INDEX idx_rp_session_expiry ON rp_session (expires_at);
