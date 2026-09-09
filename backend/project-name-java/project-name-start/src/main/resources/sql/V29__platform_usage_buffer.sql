-- C3 上行的本地用量缓冲区。
--
-- 主键就是幂等键：重放天然是无操作，不需要任何额外的去重查询，
-- 也不可能因为并发写出现两行同键。
--
-- 一条语句一列，且不用 MySQL 专有语法——测试上下文跑在 H2 上，
-- V26 就是在这件事上先失败了一次。
CREATE TABLE platform_usage_event (
    idempotency_key VARCHAR(191) NOT NULL,
    workspace_id    VARCHAR(255) NOT NULL,
    metric          VARCHAR(128) NOT NULL,
    amount          BIGINT       NOT NULL,
    end_user_id     VARCHAR(255) NULL,
    task_id         VARCHAR(128) NULL,
    occurred_at     DATETIME(3)  NOT NULL,
    claim_token     VARCHAR(64)  NULL,
    claimed_at      DATETIME(3)  NULL,
    flushed_at      DATETIME(3)  NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(512) NULL,
    PRIMARY KEY (idempotency_key)
);

-- 冲洗任务的唯一查询形状：找还没冲洗、且没有活认领的行，按发生顺序取一批。
CREATE INDEX idx_usage_pending ON platform_usage_event (flushed_at, claimed_at, occurred_at);

-- 认领后按令牌读回。
CREATE INDEX idx_usage_claim ON platform_usage_event (claim_token);
