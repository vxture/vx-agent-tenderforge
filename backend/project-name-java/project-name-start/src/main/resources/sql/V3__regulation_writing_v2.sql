-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-29

ALTER TABLE planning_project ADD COLUMN region_code VARCHAR(32) NOT NULL DEFAULT '610000';
ALTER TABLE planning_project ADD COLUMN outside_urban_development_boundary BOOLEAN NULL;
ALTER TABLE planning_project ADD COLUMN template_version VARCHAR(40) NOT NULL DEFAULT 'LEGACY_V1';
ALTER TABLE planning_project ADD COLUMN rule_snapshot_version INT NOT NULL DEFAULT 0;

ALTER TABLE planning_decision ADD COLUMN decision_code VARCHAR(80) NULL;

ALTER TABLE chapter_input_requirement ADD COLUMN gap_type VARCHAR(24) NOT NULL DEFAULT 'DATA';
ALTER TABLE chapter_input_requirement ADD COLUMN marker VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE planning_chapter ADD COLUMN current_draft_grade VARCHAR(32) NULL;
ALTER TABLE planning_chapter ADD COLUMN current_content_origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL_EDITED';

CREATE TABLE planning_rule (
    id VARCHAR(160) NOT NULL COMMENT '规则稳定ID，使用来源和条款编码',
    source_title VARCHAR(300) NOT NULL COMMENT '正式来源名称',
    source_version VARCHAR(80) NOT NULL COMMENT '来源版本或发布年份',
    clause VARCHAR(80) NOT NULL COMMENT '原文条款号',
    rule_text LONGTEXT NOT NULL COMMENT '经专业复核的原子规则原文',
    strength VARCHAR(32) NOT NULL COMMENT '规则强度',
    region_code VARCHAR(32) NOT NULL COMMENT '适用行政区划代码',
    effective_from DATE NOT NULL COMMENT '规则生效日期',
    effective_to DATE NULL COMMENT '规则失效日期',
    status VARCHAR(24) NOT NULL COMMENT 'ACTIVE、RETIRED',
    review_status VARCHAR(32) NOT NULL COMMENT 'REVIEW_REQUIRED、APPROVED、REJECTED',
    resolution_status VARCHAR(32) NOT NULL COMMENT 'RESOLVED、UNRESOLVED',
    source_locator VARCHAR(200) NOT NULL COMMENT '正式文件中的定位',
    supersedes_rule_id VARCHAR(160) NULL COMMENT '明确替代的旧规则ID',
    conflict_group VARCHAR(120) NULL COMMENT '冲突主题稳定编码',
    priority INT NOT NULL DEFAULT 0 COMMENT '已审核规则内部匹配优先级',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) COMMENT='经审核后可发布的原子规划规则表，待复核规则不得进入生成载荷';
CREATE INDEX idx_planning_rule_filter
    ON planning_rule(region_code, status, review_status, resolution_status, effective_from);

CREATE TABLE planning_rule_chapter (
    rule_id VARCHAR(160) NOT NULL,
    chapter_code VARCHAR(24) NOT NULL,
    PRIMARY KEY (rule_id, chapter_code),
    CONSTRAINT fk_rule_chapter_rule FOREIGN KEY (rule_id) REFERENCES planning_rule(id)
) COMMENT='规则与规程版正文章节的适用关系';

CREATE TABLE planning_rule_condition (
    id VARCHAR(36) NOT NULL,
    rule_id VARCHAR(160) NOT NULL,
    input_code VARCHAR(80) NOT NULL COMMENT '事实或决策稳定编码',
    input_type VARCHAR(24) NOT NULL COMMENT 'FACT、DECISION',
    operator_code VARCHAR(32) NOT NULL COMMENT 'EQUALS、IS_CONFIRMED',
    expected_value VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rule_condition_rule FOREIGN KEY (rule_id) REFERENCES planning_rule(id)
) COMMENT='规则适用条件，业务后端负责匹配，Dify不得二次裁决';

CREATE TABLE project_rule_snapshot (
    id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    chapter_code VARCHAR(24) NOT NULL,
    version_number INT NOT NULL,
    effective_on DATE NOT NULL,
    status VARCHAR(24) NOT NULL COMMENT 'CREATED、USED、FAILED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_rule_snapshot UNIQUE (project_id, version_number),
    CONSTRAINT fk_rule_snapshot_project FOREIGN KEY (project_id) REFERENCES planning_project(id)
) COMMENT='每次章节生成前形成的不可变适用规则快照';
CREATE INDEX idx_rule_snapshot_project ON project_rule_snapshot(project_id, chapter_code, created_at);

CREATE TABLE project_rule_snapshot_item (
    id VARCHAR(36) NOT NULL,
    snapshot_id VARCHAR(36) NOT NULL,
    rule_id VARCHAR(160) NOT NULL,
    source_title VARCHAR(300) NOT NULL,
    source_version VARCHAR(80) NOT NULL,
    clause VARCHAR(80) NOT NULL,
    rule_text LONGTEXT NOT NULL,
    strength VARCHAR(32) NOT NULL,
    source_locator VARCHAR(200) NOT NULL,
    match_reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_snapshot_rule UNIQUE (snapshot_id, rule_id),
    CONSTRAINT fk_snapshot_item_snapshot FOREIGN KEY (snapshot_id) REFERENCES project_rule_snapshot(id)
) COMMENT='生成时实际命中的规则原文、强度与匹配原因快照';

ALTER TABLE chapter_version ADD COLUMN content_origin VARCHAR(32) NOT NULL DEFAULT 'MANUAL_EDITED';
ALTER TABLE chapter_version ADD COLUMN draft_grade VARCHAR(32) NULL;
ALTER TABLE chapter_version ADD COLUMN rule_snapshot_id VARCHAR(36) NULL;

ALTER TABLE generation_task ADD COLUMN operation_code VARCHAR(24) NOT NULL DEFAULT 'GENERATE';
ALTER TABLE generation_task ADD COLUMN rule_snapshot_id VARCHAR(36) NULL;
ALTER TABLE generation_task ADD COLUMN workflow_version VARCHAR(24) NULL;

CREATE TABLE chapter_version_citation (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    reference_type VARCHAR(24) NOT NULL,
    reference_id VARCHAR(160) NOT NULL,
    label VARCHAR(500) NOT NULL,
    locator VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_v2_citation_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='V2章节引用快照，支持长规则ID';

CREATE TABLE chapter_version_gap (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    gap_code VARCHAR(120) NOT NULL,
    gap_type VARCHAR(24) NOT NULL,
    description VARCHAR(500) NOT NULL,
    marker VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_version_gap UNIQUE (chapter_version_id, gap_code),
    CONSTRAINT fk_version_gap_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='章节版本中未解决的类型化输入缺口';

CREATE TABLE chapter_version_placeholder (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    gap_code VARCHAR(120) NOT NULL,
    gap_type VARCHAR(24) NOT NULL,
    marker VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_version_placeholder UNIQUE (chapter_version_id, gap_code),
    CONSTRAINT fk_version_placeholder_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='章节正文中实际保留的可检索占位标记';

CREATE TABLE chapter_numeric_usage (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    literal_value VARCHAR(160) NOT NULL,
    reference_type VARCHAR(24) NOT NULL,
    reference_id VARCHAR(160) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_numeric_usage_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='章节正文数字与事实、决策或规则来源映射';

CREATE TABLE chapter_rule_usage (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    rule_id VARCHAR(160) NOT NULL,
    quote_text LONGTEXT NOT NULL,
    applied_strength VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rule_usage_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='章节正文采用的规则句和实际表达强度';

CREATE TABLE chapter_version_warning (
    id VARCHAR(36) NOT NULL,
    chapter_version_id VARCHAR(36) NOT NULL,
    warning_text VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_version_warning_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='模型发现但不改变稳定缺口集合的专业提示';
