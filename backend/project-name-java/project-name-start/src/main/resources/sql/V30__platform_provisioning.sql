-- C3 下发：开通/停用事件的投递记录与工作空间开通状态。
--
-- 一条语句一列、不用 MySQL 专有语法——测试上下文跑在 H2 上。

-- 投递记录。主键就是平台的投递标识：重复投递撞主键，而撞主键正是
-- 「这条已经处理过了」的正确答案，不需要先查后插那一对可以被并发穿过的语句。
--
-- 刻意<不>存 payload 原文。存下来对排查有点用，但那是把平台主数据复制进产品库
-- ——通则明确产品只持引用。要复盘一次投递，这里的 type/workspace/seq/结果
-- 足够定位，剩下的去平台侧查。
CREATE TABLE platform_provision_delivery (
    delivery_id  VARCHAR(191) NOT NULL,
    event_type   VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(255) NULL,
    seq          BIGINT       NOT NULL,
    outcome      VARCHAR(32)  NOT NULL,
    received_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (delivery_id)
);

CREATE INDEX idx_provision_delivery_received ON platform_provision_delivery (received_at);

-- 工作空间的开通状态。
--
-- 这是<记录>，不是门控。门控只有 C2 一处——停用之后平台不再给 tier，界面自然关闭。
-- 在这里再判一次会造出第二个说了算的地方，而两处的答案迟早分叉。
-- 这张表存在是为了能在不问平台的情况下回答「这个空间什么时候被停用的」，
-- 以及让数据留存期从一个本地事实开始算。
CREATE TABLE platform_workspace_provision (
    workspace_id      VARCHAR(255) NOT NULL,
    product           VARCHAR(64)  NOT NULL,
    state             VARCHAR(32)  NOT NULL,
    last_seq          BIGINT       NOT NULL DEFAULT 0,
    provisioned_at    DATETIME(3)  NULL,
    deprovisioned_at  DATETIME(3)  NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (workspace_id, product)
);
