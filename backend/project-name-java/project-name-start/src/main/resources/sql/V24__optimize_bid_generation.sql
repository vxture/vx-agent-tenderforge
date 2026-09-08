-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-28

ALTER TABLE bid_generation_snapshot ADD COLUMN solution_contract LONGTEXT NULL
    COMMENT '目录策略冻结形成的解决方案架构契约，供全部正文lane共享'
    AFTER outline_version;

UPDATE bid_generation_snapshot
SET solution_contract = CONCAT(
    '# 解决方案架构契约\n方案范围：', bid_title,
    '的技术投标响应。\n全局约束：保持冻结事实、评分要求、术语和承诺一致，不编造投标人事实。'
)
WHERE solution_contract IS NULL;

ALTER TABLE bid_generation_snapshot MODIFY COLUMN solution_contract LONGTEXT NOT NULL
    COMMENT '目录策略冻结形成的解决方案架构契约，供全部正文lane共享';

CREATE TABLE bid_snapshot_branch_blueprint (
    id VARCHAR(36) NOT NULL COMMENT '二级技术域蓝图ID',
    snapshot_id VARCHAR(36) NOT NULL COMMENT '不可变正文快照ID',
    branch_outline_id VARCHAR(36) NOT NULL COMMENT '快照内二级目录的source_outline_id',
    input_hash CHAR(64) NOT NULL COMMENT '蓝图严格输入SHA-256',
    blueprint_payload LONGTEXT NOT NULL COMMENT '通过严格契约校验的二级技术域蓝图JSON',
    output_hash CHAR(64) NOT NULL COMMENT '蓝图结构化结果SHA-256',
    prompt_version VARCHAR(64) NOT NULL COMMENT '蓝图提示词版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_snapshot_branch_blueprint
        UNIQUE (snapshot_id, branch_outline_id, input_hash),
    CONSTRAINT fk_bid_snapshot_branch_blueprint_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES bid_generation_snapshot(id)
) COMMENT='正文生成复用的二级技术域蓝图；模型成功后只写一次，重试直接复用';

CREATE INDEX idx_bid_snapshot_branch_blueprint_lookup
    ON bid_snapshot_branch_blueprint(snapshot_id, branch_outline_id);

ALTER TABLE bid_ai_run ADD COLUMN reasoning_tokens BIGINT NULL
    COMMENT '模型报告的思考Token数，不包含最终答案Token';
ALTER TABLE bid_ai_run ADD COLUMN cached_input_tokens BIGINT NULL
    COMMENT '模型报告的命中缓存输入Token数';
ALTER TABLE bid_ai_run ADD COLUMN model_attempt_count INT NOT NULL DEFAULT 1
    COMMENT '本次业务运行内部的模型请求或结构修复次数';
ALTER TABLE bid_ai_run ADD COLUMN current_attempt_id VARCHAR(36) NULL
    COMMENT '当前逻辑运行尝试ID，用于完成或失败时精确落账';

CREATE TABLE bid_ai_run_attempt (
    id VARCHAR(36) NOT NULL COMMENT 'AI运行尝试ID',
    ai_run_id VARCHAR(36) NOT NULL COMMENT '逻辑AI运行ID',
    attempt_no INT NOT NULL COMMENT '同一幂等运行的业务尝试序号，从1开始',
    status VARCHAR(24) NOT NULL COMMENT 'RUNNING、SUCCEEDED、FAILED或INTERRUPTED',
    duration_ms BIGINT NULL COMMENT '该次尝试耗时，单位毫秒',
    input_tokens BIGINT NULL COMMENT '该次尝试输入Token',
    output_tokens BIGINT NULL COMMENT '该次尝试输出Token',
    reasoning_tokens BIGINT NULL COMMENT '该次尝试思考Token',
    cached_input_tokens BIGINT NULL COMMENT '该次尝试命中缓存的输入Token',
    model_attempt_count INT NOT NULL DEFAULT 1 COMMENT '提供方重试及结构修复请求数',
    finish_reason VARCHAR(32) NULL COMMENT '模型finish_reason',
    response_length INT NULL COMMENT '模型原始响应字符数',
    response_hash CHAR(64) NULL COMMENT '模型原始响应SHA-256',
    error_code VARCHAR(64) NULL COMMENT '稳定错误码',
    error_message VARCHAR(1000) NULL COMMENT '过滤后的失败摘要',
    failure_reason VARCHAR(1000) NULL COMMENT '过滤后的字段级诊断',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    finished_at TIMESTAMP NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_ai_run_attempt UNIQUE (ai_run_id, attempt_no),
    CONSTRAINT fk_bid_ai_run_attempt_run FOREIGN KEY (ai_run_id)
        REFERENCES bid_ai_run(id)
) COMMENT='每次真实业务尝试的AI元数据；重试不覆盖历史Token和耗时';

CREATE INDEX idx_bid_ai_run_attempt_trace
    ON bid_ai_run_attempt(ai_run_id, attempt_no, status);
