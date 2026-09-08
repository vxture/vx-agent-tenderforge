-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-30

ALTER TABLE planning_project
    ADD COLUMN business_stage VARCHAR(32) NOT NULL DEFAULT 'EMPTY'
    COMMENT 'V3业务阶段：EMPTY、PARSING、NEEDS_CONFIRMATION、GENERATING、DRAFT_READY、EXPORTED';

CREATE TABLE project_detail_sheet (
    id VARCHAR(36) NOT NULL COMMENT '项目细节确认单ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，每个项目仅一张当前确认单',
    schema_version VARCHAR(40) NOT NULL COMMENT '固定字段版本，初始DETAIL_SHEET_V1',
    source_revision BIGINT NOT NULL DEFAULT 0 COMMENT '成功合并资料解析结果的次数',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '整张确认单乐观锁版本',
    updated_by VARCHAR(36) NOT NULL COMMENT '最近更新用户ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_project_detail_sheet_project UNIQUE (project_id),
    CONSTRAINT fk_detail_sheet_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_detail_sheet_user FOREIGN KEY (updated_by) REFERENCES app_user(id)
) COMMENT='V3项目细节确认单，用户一次编辑并整体确认';

CREATE TABLE project_detail_item (
    id VARCHAR(36) NOT NULL COMMENT '确认单字段ID，UUID',
    sheet_id VARCHAR(36) NOT NULL COMMENT '确认单ID',
    group_code VARCHAR(24) NOT NULL COMMENT '字段分组：BASIC、CURRENT、INTENT',
    field_code VARCHAR(80) NOT NULL COMMENT 'DETAIL_SHEET_V1固定字段编码',
    field_name VARCHAR(160) NOT NULL COMMENT '字段展示名称',
    field_value LONGTEXT NOT NULL COMMENT '当前采用内容，空字符串表示缺失',
    issue_code VARCHAR(24) NOT NULL COMMENT '问题状态：NONE、CONFLICT、MISSING',
    alternatives_json LONGTEXT NOT NULL COMMENT '冲突候选值JSON数组',
    sort_order INT NOT NULL COMMENT '固定展示顺序',
    PRIMARY KEY (id),
    CONSTRAINT uk_project_detail_item_code UNIQUE (sheet_id, field_code),
    CONSTRAINT fk_detail_item_sheet FOREIGN KEY (sheet_id) REFERENCES project_detail_sheet(id)
) COMMENT='V3确认单固定字段当前值及冲突候选';

CREATE TABLE project_detail_source (
    id VARCHAR(36) NOT NULL COMMENT '字段出处关系ID，UUID',
    item_id VARCHAR(36) NOT NULL COMMENT '确认单字段ID',
    evidence_id VARCHAR(36) NOT NULL COMMENT '证据片段ID',
    rationale VARCHAR(500) NOT NULL COMMENT '采用或保留该出处的原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_detail_source UNIQUE (item_id, evidence_id),
    CONSTRAINT fk_detail_source_item FOREIGN KEY (item_id) REFERENCES project_detail_item(id),
    CONSTRAINT fk_detail_source_evidence FOREIGN KEY (evidence_id) REFERENCES evidence_ref(id)
) COMMENT='确认单字段与原始资料证据的追溯关系';

CREATE TABLE project_input_snapshot (
    id VARCHAR(36) NOT NULL COMMENT '项目输入快照ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    version_number INT NOT NULL COMMENT '项目内递增快照版本',
    request_id VARCHAR(64) NOT NULL COMMENT '客户端一次确认幂等键',
    schema_version VARCHAR(40) NOT NULL COMMENT '确认单字段版本',
    content_json LONGTEXT NOT NULL COMMENT '确认时全部字段及问题状态JSON快照',
    document_revision BIGINT NOT NULL COMMENT '确认时资料合并版本',
    confirmed_by VARCHAR(36) NOT NULL COMMENT '确认用户ID',
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '确认时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_input_snapshot_version UNIQUE (project_id, version_number),
    CONSTRAINT uk_input_snapshot_request UNIQUE (project_id, request_id),
    CONSTRAINT fk_input_snapshot_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_input_snapshot_user FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
) COMMENT='一次人工确认形成的不可变完整输入快照';

ALTER TABLE generation_task MODIFY COLUMN chapter_id VARCHAR(36) NULL;
ALTER TABLE generation_task
    ADD COLUMN task_type VARCHAR(24) NOT NULL DEFAULT 'CHAPTER' COMMENT '任务类型：CHAPTER、REPORT';
ALTER TABLE generation_task
    ADD COLUMN input_snapshot_id VARCHAR(36) NULL COMMENT 'REPORT任务绑定的不可变输入快照ID';
ALTER TABLE generation_task
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '客户端幂等请求ID';
ALTER TABLE generation_task
    ADD COLUMN current_chapter_code VARCHAR(24) NULL COMMENT '后台正在处理的章节编码';
ALTER TABLE generation_task
    ADD COLUMN completed_chapter_count INT NOT NULL DEFAULT 0 COMMENT '已完成章节数';
ALTER TABLE generation_task
    ADD COLUMN total_chapter_count INT NOT NULL DEFAULT 1 COMMENT '任务章节总数';
ALTER TABLE generation_task
    ADD CONSTRAINT uk_generation_task_request UNIQUE (project_id, request_id);
ALTER TABLE generation_task
    ADD CONSTRAINT fk_generation_task_snapshot FOREIGN KEY (input_snapshot_id) REFERENCES project_input_snapshot(id);

CREATE INDEX idx_detail_source_item ON project_detail_source(item_id);
CREATE INDEX idx_input_snapshot_project ON project_input_snapshot(project_id, version_number);
CREATE INDEX idx_generation_report_project ON generation_task(project_id, task_type, created_at);
