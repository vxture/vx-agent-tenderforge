-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-01

ALTER TABLE generation_task
    ADD COLUMN progress_percent SMALLINT NOT NULL DEFAULT 0
    COMMENT '任务总体进度，范围0至100，来源为已完成业务节点';
ALTER TABLE generation_task
    ADD COLUMN stage_code VARCHAR(40) NOT NULL DEFAULT 'QUEUED'
    COMMENT '当前业务阶段稳定编码';
ALTER TABLE generation_task
    ADD COLUMN stage_label VARCHAR(100) NOT NULL DEFAULT '等待执行'
    COMMENT '当前业务阶段显示名称';
ALTER TABLE generation_task
    ADD COLUMN completed_step_count INT NOT NULL DEFAULT 0
    COMMENT '当前阶段已完成步骤数';
ALTER TABLE generation_task
    ADD COLUMN total_step_count INT NOT NULL DEFAULT 0
    COMMENT '当前阶段总步骤数';
ALTER TABLE generation_task
    ADD COLUMN provider_task_id VARCHAR(100) NULL
    COMMENT '外部提供方任务标识，仅管理员排障使用';
ALTER TABLE generation_task
    ADD COLUMN provider_workflow_run_id VARCHAR(100) NULL
    COMMENT '外部提供方工作流运行标识，仅管理员排障使用';
ALTER TABLE generation_task
    ADD COLUMN last_heartbeat_at TIMESTAMP NULL
    COMMENT '最近一次安全进度事件接收时间，时区：UTC+8';
ALTER TABLE generation_task
    ADD COLUMN trace_id VARCHAR(64) NULL
    COMMENT '任务发起请求追踪号';

UPDATE generation_task
SET progress_percent = 100,
    stage_code = 'COMPLETED',
    stage_label = '执行完成',
    completed_step_count = total_chapter_count,
    total_step_count = total_chapter_count,
    last_heartbeat_at = COALESCE(finished_at, created_at)
WHERE status = 'SUCCEEDED';

UPDATE generation_task
SET stage_code = 'FAILED',
    stage_label = '执行失败',
    last_heartbeat_at = COALESCE(finished_at, started_at, created_at)
WHERE status = 'FAILED';

UPDATE generation_task
SET progress_percent = 1,
    stage_code = 'PREPARING',
    stage_label = '准备执行',
    last_heartbeat_at = COALESCE(started_at, created_at)
WHERE status = 'RUNNING';

CREATE TABLE generation_task_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务事件自增ID，用于稳定排序',
    task_id VARCHAR(36) NOT NULL COMMENT '生成任务ID，关联generation_task.id',
    event_type VARCHAR(40) NOT NULL COMMENT '事件类型：启动、节点完成、失败或完成',
    stage_code VARCHAR(40) NOT NULL COMMENT '事件发生时业务阶段编码',
    stage_label VARCHAR(100) NOT NULL COMMENT '事件发生时业务阶段显示名称',
    progress_percent SMALLINT NOT NULL COMMENT '事件发生时总体进度，范围0至100',
    completed_step_count INT NOT NULL DEFAULT 0 COMMENT '当前阶段已完成步骤数',
    total_step_count INT NOT NULL DEFAULT 0 COMMENT '当前阶段总步骤数',
    chapter_code VARCHAR(24) NULL COMMENT '关联章节编码，如CH01',
    provider_node_id VARCHAR(100) NULL COMMENT '外部提供方节点标识',
    provider_node_title VARCHAR(160) NULL COMMENT '外部提供方节点标题',
    message VARCHAR(500) NULL COMMENT '白名单业务摘要，不保存输入输出或堆栈',
    error_code VARCHAR(80) NULL COMMENT '稳定错误码',
    duration_ms BIGINT NULL COMMENT '外部节点耗时，单位：毫秒',
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件接收时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_task_event_task FOREIGN KEY (task_id) REFERENCES generation_task(id)
) COMMENT='AI生成任务安全事件时间线，不保存Prompt、正文、节点输入输出、密钥或堆栈';

CREATE INDEX idx_task_event_task_time
    ON generation_task_event(task_id, occurred_at, id);
