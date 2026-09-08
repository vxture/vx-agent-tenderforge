-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-27

CREATE TABLE bid_outline_stage_result (
    id VARCHAR(36) PRIMARY KEY COMMENT '阶段结果ID，UUID',
    task_id VARCHAR(36) NOT NULL COMMENT '目录任务ID，关联 bid_outline_task.id',
    stage_type VARCHAR(32) NOT NULL COMMENT '阶段：STRATEGY、SKELETON、EXPANSION',
    batch_index INT NOT NULL DEFAULT 0 COMMENT '批次序号，策略和骨架固定为0，扩展从1开始',
    status VARCHAR(24) NOT NULL COMMENT '状态：RUNNING、SUCCEEDED、FAILED',
    input_hash CHAR(64) NOT NULL COMMENT '严格类型输入的SHA-256，用于幂等恢复',
    output_payload LONGTEXT NULL COMMENT '通过Java/Pydantic契约校验的阶段结果JSON，包含项目派生内容，敏感',
    output_hash CHAR(64) NULL COMMENT '阶段结果SHA-256，不包含模型凭据',
    model_name VARCHAR(100) NOT NULL COMMENT '该阶段实际使用的模型名称，不包含凭据',
    duration_ms BIGINT NULL COMMENT '最近一次执行耗时，单位：毫秒',
    attempt_count INT NOT NULL DEFAULT 1 COMMENT '该阶段开始执行的累计次数',
    error_code VARCHAR(64) NULL COMMENT '稳定错误码，不包含堆栈和凭据',
    error_message VARCHAR(1000) NULL COMMENT '过滤后的失败摘要，不保存提示词或模型原文',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次开始时间，时区：数据库会话时区',
    finished_at TIMESTAMP NULL COMMENT '最近一次完成时间，时区：数据库会话时区',
    CONSTRAINT uk_bid_outline_stage UNIQUE (task_id, stage_type, batch_index),
    CONSTRAINT fk_bid_outline_stage_task FOREIGN KEY (task_id)
        REFERENCES bid_outline_task(id) ON DELETE CASCADE
) COMMENT = '目录生成阶段结果；由Worker按阶段实时写入，随目录任务保留，用于重试恢复和耗时诊断';

CREATE INDEX idx_bid_outline_stage_status
    ON bid_outline_stage_result(task_id, status, stage_type, batch_index);
