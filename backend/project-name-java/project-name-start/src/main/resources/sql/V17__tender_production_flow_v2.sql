-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-03

ALTER TABLE bid_document ADD COLUMN interpretation_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT '解读阶段状态：DRAFT, REVIEW, FROZEN';
ALTER TABLE bid_document ADD COLUMN interpretation_version INT NOT NULL DEFAULT 0
    COMMENT '当前解读版本号，从0开始';
ALTER TABLE bid_document ADD COLUMN interpretation_hash CHAR(64) NULL
    COMMENT '冻结解读规范化内容的SHA-256';
ALTER TABLE bid_document ADD COLUMN outline_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT '目录阶段状态：DRAFT, REVIEW, FROZEN';
ALTER TABLE bid_document ADD COLUMN outline_version INT NOT NULL DEFAULT 0
    COMMENT '当前目录版本号，从0开始';
ALTER TABLE bid_document ADD COLUMN outline_hash CHAR(64) NULL
    COMMENT '冻结目录规范化内容的SHA-256';
ALTER TABLE bid_document ADD COLUMN content_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT '正文阶段状态：DRAFT, GENERATING, REVIEW, FROZEN';
ALTER TABLE bid_document ADD COLUMN content_version INT NOT NULL DEFAULT 0
    COMMENT '当前成稿版本号，从0开始';
ALTER TABLE bid_document ADD COLUMN content_hash CHAR(64) NULL
    COMMENT '冻结正文规范化内容的SHA-256';
ALTER TABLE bid_document ADD COLUMN stale_reason VARCHAR(1000) NULL
    COMMENT '正文需要复核的具体上游变更原因';

CREATE TABLE bid_interpretation_version (
    id VARCHAR(36) NOT NULL COMMENT '解读版本ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID，关联bid_document.id',
    version_no INT NOT NULL COMMENT '标书内递增版本号',
    status VARCHAR(24) NOT NULL COMMENT '版本状态：REVIEW或FROZEN',
    content_hash CHAR(64) NOT NULL COMMENT '规范化解读内容SHA-256',
    created_by VARCHAR(36) NOT NULL COMMENT '创建用户ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    frozen_at TIMESTAMP NULL COMMENT '冻结时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_interpretation_version UNIQUE (bid_id, version_no),
    CONSTRAINT fk_bid_interpretation_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_interpretation_user FOREIGN KEY (created_by) REFERENCES app_user(id)
) COMMENT='招标文件技术解读不可变版本，随人工保存或冻结创建';

CREATE TABLE bid_requirement_item (
    id VARCHAR(36) NOT NULL COMMENT '版本要求项ID',
    interpretation_version_id VARCHAR(36) NOT NULL COMMENT '解读版本ID',
    source_criterion_id VARCHAR(36) NOT NULL COMMENT '当前工作区要求项ID',
    item_type VARCHAR(24) NOT NULL COMMENT '类型：SCORING, REJECTION, FORMAT, FACT',
    title VARCHAR(200) NOT NULL COMMENT '要求标题',
    description LONGTEXT NOT NULL COMMENT '要求完整描述',
    score DECIMAL(10,2) NULL COMMENT '评分分值，单位：分',
    source_locator VARCHAR(255) NOT NULL COMMENT '原文页码、段落或表格定位',
    source_excerpt LONGTEXT NULL COMMENT '原文证据摘录',
    scope VARCHAR(24) NOT NULL COMMENT '范围：TECHNICAL, COMMERCIAL, MIXED, FORMAT',
    confidence VARCHAR(16) NOT NULL COMMENT '置信度：HIGH, MEDIUM, LOW',
    sort_order INT NOT NULL COMMENT '版本内展示顺序，从0开始',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_requirement_version FOREIGN KEY (interpretation_version_id)
        REFERENCES bid_interpretation_version(id)
) COMMENT='解读版本内的评分点、废标条款、格式要求和事实快照';
CREATE INDEX idx_bid_requirement_version ON bid_requirement_item(interpretation_version_id, sort_order);

CREATE TABLE bid_requirement_conflict (
    id VARCHAR(36) NOT NULL COMMENT '冲突项ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    conflict_key VARCHAR(200) NOT NULL COMMENT '冲突主题，例如前置代理服务器内存',
    left_value VARCHAR(1000) NOT NULL COMMENT '冲突值A',
    left_source VARCHAR(255) NOT NULL COMMENT '冲突值A原文定位',
    right_value VARCHAR(1000) NOT NULL COMMENT '冲突值B',
    right_source VARCHAR(255) NOT NULL COMMENT '冲突值B原文定位',
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN或RESOLVED',
    resolution VARCHAR(2000) NULL COMMENT '人工冻结的唯一口径',
    resolved_by VARCHAR(36) NULL COMMENT '处理用户ID',
    resolved_at TIMESTAMP NULL COMMENT '处理时间，时区UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_conflict_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_conflict_user FOREIGN KEY (resolved_by) REFERENCES app_user(id)
) COMMENT='模型或规则发现的招标文件数值与口径冲突，必须人工处理';
CREATE INDEX idx_bid_conflict_status ON bid_requirement_conflict(bid_id, status, created_at);

CREATE TABLE bid_frozen_fact (
    id VARCHAR(36) NOT NULL COMMENT '冻结事实ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    interpretation_version_id VARCHAR(36) NOT NULL COMMENT '来源解读版本ID',
    fact_type VARCHAR(24) NOT NULL COMMENT '类型：METRIC, TERM, FIXED_FACT',
    fact_name VARCHAR(200) NOT NULL COMMENT '指标、术语或事实名称',
    fact_value VARCHAR(2000) NOT NULL COMMENT '后续生成必须遵守的唯一口径',
    source_locator VARCHAR(255) NOT NULL COMMENT '原文定位或人工冲突处理定位',
    forbidden_values VARCHAR(2000) NULL COMMENT '禁止出现的冲突值，多个值使用换行分隔',
    sort_order INT NOT NULL COMMENT '展示及提示词顺序，从0开始',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '冻结时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_frozen_fact_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_frozen_fact_version FOREIGN KEY (interpretation_version_id)
        REFERENCES bid_interpretation_version(id)
) COMMENT='经人工确认的指标、术语和固定事实字典';
CREATE INDEX idx_bid_frozen_fact_bid ON bid_frozen_fact(bid_id, sort_order);

CREATE TABLE bid_generation_unit (
    id VARCHAR(36) NOT NULL COMMENT '正文生成单元ID',
    task_id VARCHAR(36) NOT NULL COMMENT '生成任务ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    chapter_id VARCHAR(36) NOT NULL COMMENT '叶子章节ID',
    unit_index INT NOT NULL COMMENT '章节内单元序号，从0开始',
    status VARCHAR(24) NOT NULL COMMENT '状态：PENDING, RUNNING, SUCCEEDED, FAILED, SKIPPED',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '已经执行的尝试次数',
    word_budget INT NOT NULL COMMENT '写作字数预算，单位：中文字符',
    summary LONGTEXT NULL COMMENT '生成完成后的上下文压缩摘要',
    error_message VARCHAR(1000) NULL COMMENT '最后一次失败原因',
    started_at TIMESTAMP NULL COMMENT '开始时间，时区UTC+8',
    finished_at TIMESTAMP NULL COMMENT '结束时间，时区UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_generation_unit UNIQUE (task_id, chapter_id, unit_index),
    CONSTRAINT fk_bid_unit_task FOREIGN KEY (task_id) REFERENCES bid_generation_task(id),
    CONSTRAINT fk_bid_unit_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_unit_chapter FOREIGN KEY (chapter_id) REFERENCES bid_chapter(id)
) COMMENT='正文生成的最小可重试写作单元，当前首期每个叶子章节一个单元';
CREATE INDEX idx_bid_unit_status ON bid_generation_unit(task_id, status, unit_index);

CREATE TABLE bid_generation_event (
    id VARCHAR(36) NOT NULL COMMENT '生成事件ID',
    task_id VARCHAR(36) NOT NULL COMMENT '生成任务ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    chapter_id VARCHAR(36) NULL COMMENT '关联章节ID，可为空',
    event_type VARCHAR(48) NOT NULL COMMENT '事件类型，例如TASK_STARTED或CHAPTER_SUCCEEDED',
    message VARCHAR(1000) NOT NULL COMMENT '面向用户的事件摘要',
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_event_task FOREIGN KEY (task_id) REFERENCES bid_generation_task(id),
    CONSTRAINT fk_bid_event_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
) COMMENT='正文生成任务的可查询事件流，不存储正文和模型密钥';
CREATE INDEX idx_bid_event_order ON bid_generation_event(bid_id, occurred_at, id);

ALTER TABLE bid_generation_task ADD COLUMN workflow_run_id VARCHAR(160) NULL
    COMMENT 'Temporal工作流运行ID；本地模式为空';
ALTER TABLE bid_generation_task ADD COLUMN snapshot_hash CHAR(64) NULL
    COMMENT '本次生成使用的冻结输入快照SHA-256';
ALTER TABLE bid_generation_task ADD COLUMN retry_count INT NOT NULL DEFAULT 0
    COMMENT '任务级重试次数';
ALTER TABLE bid_generation_task ADD COLUMN heartbeat_at TIMESTAMP NULL
    COMMENT '最近活动心跳时间，时区UTC+8';

CREATE TABLE bid_chapter_version (
    id VARCHAR(36) NOT NULL COMMENT '章节版本ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    chapter_id VARCHAR(36) NOT NULL COMMENT '章节ID',
    version_no INT NOT NULL COMMENT '章节内递增版本号',
    source_type VARCHAR(24) NOT NULL COMMENT '来源：AI, MANUAL, AI_REVISION',
    content LONGTEXT NOT NULL COMMENT '兼容Tiptap的受限HTML正文',
    content_hash CHAR(64) NOT NULL COMMENT '正文规范化SHA-256',
    summary LONGTEXT NULL COMMENT '供后续章节保持连贯的摘要',
    created_by VARCHAR(36) NULL COMMENT '人工版本创建用户；AI版本为空',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_chapter_version UNIQUE (chapter_id, version_no),
    CONSTRAINT fk_bid_chapter_version_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_chapter_version_chapter FOREIGN KEY (chapter_id) REFERENCES bid_chapter(id),
    CONSTRAINT fk_bid_chapter_version_user FOREIGN KEY (created_by) REFERENCES app_user(id)
) COMMENT='章节正文不可变版本，当前bid_chapter保存活动版本以兼容编辑器';
CREATE INDEX idx_bid_chapter_version_latest ON bid_chapter_version(chapter_id, version_no);

CREATE TABLE bid_review_issue (
    id VARCHAR(36) NOT NULL COMMENT '审查问题ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    chapter_id VARCHAR(36) NULL COMMENT '关联章节ID，全局问题可为空',
    severity VARCHAR(16) NOT NULL COMMENT '严重度：ERROR或WARNING',
    issue_code VARCHAR(64) NOT NULL COMMENT '稳定问题编码',
    message VARCHAR(1000) NOT NULL COMMENT '问题描述',
    suggestion VARCHAR(2000) NOT NULL COMMENT '可执行整改建议',
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN, RESOLVED, IGNORED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发现时间，时区UTC+8',
    resolved_at TIMESTAMP NULL COMMENT '处理时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_review_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
) COMMENT='正文与排版审查问题，阻断级问题未解决时不得冻结成稿';
CREATE INDEX idx_bid_review_status ON bid_review_issue(bid_id, status, severity);

CREATE TABLE bid_layout_job (
    id VARCHAR(36) NOT NULL COMMENT '排版任务ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    status VARCHAR(24) NOT NULL COMMENT '状态：PENDING, RUNNING, SUCCEEDED, FAILED',
    workflow_run_id VARCHAR(160) NULL COMMENT 'Temporal工作流运行ID；本地模式记录local前缀ID',
    input_hash CHAR(64) NOT NULL COMMENT '冻结正文与排版配置SHA-256',
    target_pages INT NOT NULL COMMENT '目标页数，单位：页',
    actual_pages INT NULL COMMENT 'QA渲染得到的实际页数，单位：页',
    qa_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'QA状态：PENDING, PASSED, FAILED',
    qa_summary VARCHAR(2000) NULL COMMENT '页数偏差、空白页和元数据检查摘要',
    error_message VARCHAR(1000) NULL COMMENT '排版失败原因',
    created_by VARCHAR(36) NOT NULL COMMENT '启动排版用户ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    finished_at TIMESTAMP NULL COMMENT '完成时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_layout_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_layout_user FOREIGN KEY (created_by) REFERENCES app_user(id)
) COMMENT='正式DOCX生产和质量检查任务';
CREATE INDEX idx_bid_layout_latest ON bid_layout_job(bid_id, created_at);

ALTER TABLE bid_export ADD COLUMN layout_job_id VARCHAR(36) NULL
    COMMENT '产生该成果的排版任务ID';
ALTER TABLE bid_export ADD COLUMN qa_status VARCHAR(24) NOT NULL DEFAULT 'NOT_CHECKED'
    COMMENT '成果QA状态：NOT_CHECKED, PASSED, FAILED';
ALTER TABLE bid_export ADD CONSTRAINT fk_bid_export_layout FOREIGN KEY (layout_job_id)
    REFERENCES bid_layout_job(id);
