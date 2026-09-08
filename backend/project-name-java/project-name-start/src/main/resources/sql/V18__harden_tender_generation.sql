-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-04

CREATE TABLE bid_generation_snapshot (
    id VARCHAR(36) NOT NULL COMMENT '不可变生成快照ID',
    task_id VARCHAR(36) NOT NULL COMMENT '生成任务ID，关联bid_generation_task.id',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID，关联bid_document.id',
    owner_id VARCHAR(36) NOT NULL COMMENT '快照创建时的标书所有者ID',
    snapshot_hash CHAR(64) NOT NULL COMMENT '全部生成输入规范化后的SHA-256',
    bid_title VARCHAR(160) NOT NULL COMMENT '快照中的标书标题',
    bidding_mode VARCHAR(16) NOT NULL COMMENT '投标方式：OPEN或BLIND',
    target_pages INT NOT NULL COMMENT '目标页数，单位：页',
    interpretation_version INT NOT NULL COMMENT '冻结解读版本号',
    outline_version INT NOT NULL COMMENT '冻结目录版本号',
    writing_bible LONGTEXT NOT NULL COMMENT '全文写作总纲，包含主题、语气和章节协同约束',
    term_registry LONGTEXT NOT NULL COMMENT '全文统一术语注册表，换行分隔',
    commitment_registry LONGTEXT NOT NULL COMMENT '全文统一承诺注册表，换行分隔',
    prompt_version VARCHAR(64) NOT NULL COMMENT '正文提示词契约版本',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_snapshot_task UNIQUE (task_id),
    CONSTRAINT uk_bid_snapshot_hash UNIQUE (task_id, snapshot_hash),
    CONSTRAINT fk_bid_snapshot_task FOREIGN KEY (task_id) REFERENCES bid_generation_task(id),
    CONSTRAINT fk_bid_snapshot_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_snapshot_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
) COMMENT='正文任务的不可变生成输入根快照，每个任务创建一次且不更新';
CREATE INDEX idx_bid_snapshot_bid ON bid_generation_snapshot(bid_id, created_at);

CREATE TABLE bid_snapshot_requirement (
    id VARCHAR(36) NOT NULL COMMENT '快照需求项ID',
    snapshot_id VARCHAR(36) NOT NULL COMMENT '生成快照ID',
    source_criterion_id VARCHAR(36) NOT NULL COMMENT '创建快照时的工作区需求项ID',
    item_type VARCHAR(24) NOT NULL COMMENT '类型：SCORING, REJECTION, FORMAT, FACT',
    title VARCHAR(200) NOT NULL COMMENT '需求标题',
    description LONGTEXT NOT NULL COMMENT '需求完整描述',
    score DECIMAL(10,2) NULL COMMENT '评分分值，单位：分',
    source_excerpt LONGTEXT NULL COMMENT '招标文件证据摘录',
    source_locator VARCHAR(255) NOT NULL COMMENT '证据页码、段落或表格定位',
    sort_order INT NOT NULL COMMENT '快照内顺序，从0开始',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_snapshot_requirement FOREIGN KEY (snapshot_id)
        REFERENCES bid_generation_snapshot(id)
) COMMENT='生成快照中的评分点、废标条款、格式要求和事实副本';
CREATE INDEX idx_bid_snapshot_requirement_order ON bid_snapshot_requirement(snapshot_id, sort_order);

CREATE TABLE bid_snapshot_outline (
    id VARCHAR(36) NOT NULL COMMENT '快照目录节点ID',
    snapshot_id VARCHAR(36) NOT NULL COMMENT '生成快照ID',
    source_outline_id VARCHAR(36) NOT NULL COMMENT '创建快照时的工作区目录节点ID',
    chapter_id VARCHAR(36) NULL COMMENT '叶子目录对应的活动章节ID，非叶子为空',
    chapter_generation_status VARCHAR(24) NULL COMMENT '快照创建时章节状态，叶子目录填写',
    parent_source_outline_id VARCHAR(36) NULL COMMENT '父目录的工作区节点ID，一级目录为空',
    level_no INT NOT NULL COMMENT '目录层级：1至3',
    title VARCHAR(200) NOT NULL COMMENT '目录标题',
    planned_pages INT NOT NULL COMMENT '叶子章节计划页数，单位：页',
    sort_order INT NOT NULL COMMENT '全文顺序，从0开始',
    task_brief LONGTEXT NOT NULL COMMENT '章节写作任务说明',
    must_keywords LONGTEXT NOT NULL COMMENT '章节必含关键词，换行分隔',
    scoring_point_ids LONGTEXT NOT NULL COMMENT '覆盖评分点ID，换行分隔',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_snapshot_outline UNIQUE (snapshot_id, source_outline_id),
    CONSTRAINT fk_bid_snapshot_outline_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES bid_generation_snapshot(id),
    CONSTRAINT fk_bid_snapshot_outline_chapter FOREIGN KEY (chapter_id) REFERENCES bid_chapter(id)
) COMMENT='生成快照中的冻结三级目录和章节契约副本';
CREATE INDEX idx_bid_snapshot_outline_order ON bid_snapshot_outline(snapshot_id, sort_order);

CREATE TABLE bid_snapshot_fact (
    id VARCHAR(36) NOT NULL COMMENT '快照冻结事实ID',
    snapshot_id VARCHAR(36) NOT NULL COMMENT '生成快照ID',
    fact_type VARCHAR(24) NOT NULL COMMENT '类型：METRIC, TERM, FIXED_FACT',
    fact_name VARCHAR(200) NOT NULL COMMENT '指标、术语或事实名称',
    fact_value VARCHAR(2000) NOT NULL COMMENT '生成必须遵守的唯一口径',
    source_locator VARCHAR(255) NOT NULL COMMENT '原文或人工冲突处理定位',
    forbidden_values LONGTEXT NOT NULL COMMENT '禁止出现的冲突值，换行分隔',
    sort_order INT NOT NULL COMMENT '快照内顺序，从0开始',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_snapshot_fact_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES bid_generation_snapshot(id)
) COMMENT='生成快照中的不可变指标、术语和固定事实';
CREATE INDEX idx_bid_snapshot_fact_order ON bid_snapshot_fact(snapshot_id, sort_order);

CREATE TABLE bid_asset_chunk (
    id VARCHAR(36) NOT NULL COMMENT '素材文本分块ID',
    asset_id VARCHAR(36) NOT NULL COMMENT '参考素材ID，关联bid_reference_asset.id',
    chunk_index INT NOT NULL COMMENT '素材内稳定分块序号，从0开始',
    heading VARCHAR(500) NOT NULL COMMENT '分块所属标题或定位摘要',
    source_locator VARCHAR(255) NOT NULL COMMENT '原文件页码、段落或表格定位',
    content LONGTEXT NOT NULL COMMENT '可检索素材文本',
    content_hash CHAR(64) NOT NULL COMMENT '分块文本SHA-256',
    character_count INT NOT NULL COMMENT '分块字符数，单位：字符',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_asset_chunk UNIQUE (asset_id, chunk_index),
    CONSTRAINT fk_bid_asset_chunk_asset FOREIGN KEY (asset_id) REFERENCES bid_reference_asset(id)
) COMMENT='标书范本和大纲一次解析后形成的稳定文本分块';
CREATE INDEX idx_bid_asset_chunk_lookup ON bid_asset_chunk(asset_id, chunk_index);

CREATE TABLE bid_snapshot_asset_chunk (
    id VARCHAR(36) NOT NULL COMMENT '快照素材分块ID',
    snapshot_id VARCHAR(36) NOT NULL COMMENT '生成快照ID',
    source_chunk_id VARCHAR(36) NOT NULL COMMENT '创建快照时的素材分块ID',
    asset_id VARCHAR(36) NOT NULL COMMENT '参考素材ID',
    asset_category VARCHAR(24) NOT NULL COMMENT '素材类别：TEMPLATE或OUTLINE',
    asset_name VARCHAR(160) NOT NULL COMMENT '素材显示名称',
    heading VARCHAR(500) NOT NULL COMMENT '分块标题或定位摘要',
    source_locator VARCHAR(255) NOT NULL COMMENT '原素材定位',
    content LONGTEXT NOT NULL COMMENT '快照内不可变素材文本',
    content_hash CHAR(64) NOT NULL COMMENT '素材分块文本SHA-256',
    chunk_index INT NOT NULL COMMENT '素材内分块序号，从0开始',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_snapshot_asset_chunk UNIQUE (snapshot_id, source_chunk_id),
    CONSTRAINT fk_bid_snapshot_asset_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES bid_generation_snapshot(id)
) COMMENT='生成快照选用的参考素材分块副本，素材删除后仍可复现';
CREATE INDEX idx_bid_snapshot_asset_order ON bid_snapshot_asset_chunk(snapshot_id, asset_id, chunk_index);

CREATE TABLE bid_ai_run (
    id VARCHAR(36) NOT NULL COMMENT 'AI运行ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    task_id VARCHAR(36) NULL COMMENT '正文生成任务ID，非正文调用可为空',
    snapshot_id VARCHAR(36) NULL COMMENT '生成快照ID，非正文调用可为空',
    generation_unit_id VARCHAR(36) NULL COMMENT '生成单元ID，非正文调用可为空',
    operation_type VARCHAR(48) NOT NULL COMMENT '操作类型：INTERPRETATION, OUTLINE, CHAPTER_UNIT, REVISION, REVIEW',
    provider VARCHAR(32) NOT NULL COMMENT 'AI提供方：DIRECT_QWEN, DIFY或UNKNOWN',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称，不包含凭据',
    prompt_version VARCHAR(64) NOT NULL COMMENT '提示词契约版本',
    input_snapshot_hash CHAR(64) NOT NULL COMMENT '规范化模型输入SHA-256',
    idempotency_key VARCHAR(255) NOT NULL COMMENT '业务幂等键，不包含密钥或正文',
    status VARCHAR(24) NOT NULL COMMENT '状态：RUNNING, SUCCEEDED, FAILED',
    duration_ms BIGINT NULL COMMENT '调用耗时，单位：毫秒',
    input_tokens BIGINT NULL COMMENT '模型报告的输入Token数',
    output_tokens BIGINT NULL COMMENT '模型报告的输出Token数',
    output_hash CHAR(64) NULL COMMENT '结构化模型输出SHA-256',
    error_code VARCHAR(64) NULL COMMENT '稳定错误码',
    error_message VARCHAR(1000) NULL COMMENT '已过滤敏感信息的错误摘要',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间，时区UTC+8',
    finished_at TIMESTAMP NULL COMMENT '结束时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_ai_run_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_bid_ai_run_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_ai_run_task FOREIGN KEY (task_id) REFERENCES bid_generation_task(id),
    CONSTRAINT fk_bid_ai_run_snapshot FOREIGN KEY (snapshot_id) REFERENCES bid_generation_snapshot(id)
) COMMENT='统一AI调用审计，只记录元数据、哈希、耗时和Token，不记录API密钥';
CREATE INDEX idx_bid_ai_run_trace ON bid_ai_run(bid_id, started_at, operation_type);

ALTER TABLE bid_generation_task ADD COLUMN snapshot_id VARCHAR(36) NULL
    COMMENT '不可变生成快照ID，新任务必须填写，历史任务为空';
ALTER TABLE bid_generation_task ADD CONSTRAINT fk_bid_task_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES bid_generation_snapshot(id);

ALTER TABLE bid_generation_unit ADD COLUMN unit_title VARCHAR(300) NOT NULL DEFAULT ''
    COMMENT '章节内分段标题或写作范围';
ALTER TABLE bid_generation_unit ADD COLUMN idempotency_key VARCHAR(255) NULL
    COMMENT '单元模型调用和持久化的稳定幂等键';
ALTER TABLE bid_generation_unit ADD COLUMN content LONGTEXT NULL
    COMMENT '该分段已生成的受限HTML正文';
ALTER TABLE bid_generation_unit ADD COLUMN content_hash CHAR(64) NULL
    COMMENT '分段正文SHA-256';
ALTER TABLE bid_generation_unit ADD COLUMN ai_run_id VARCHAR(36) NULL
    COMMENT '最近一次AI运行ID';
ALTER TABLE bid_generation_unit ADD COLUMN previous_summary LONGTEXT NULL
    COMMENT '生成该分段时使用的前文压缩摘要';
ALTER TABLE bid_generation_unit ADD CONSTRAINT uk_bid_generation_unit_key UNIQUE (idempotency_key);
ALTER TABLE bid_generation_unit ADD CONSTRAINT fk_bid_generation_unit_ai_run FOREIGN KEY (ai_run_id)
    REFERENCES bid_ai_run(id);

ALTER TABLE bid_chapter_version ADD COLUMN generation_unit_id VARCHAR(36) NULL
    COMMENT '产生该版本的生成单元ID，人工版本为空';
ALTER TABLE bid_chapter_version ADD COLUMN provider VARCHAR(32) NULL
    COMMENT 'AI版本使用的提供方，人工版本为空';
ALTER TABLE bid_chapter_version ADD COLUMN model_name VARCHAR(100) NULL
    COMMENT 'AI版本使用的模型，人工版本为空';
ALTER TABLE bid_chapter_version ADD COLUMN prompt_version VARCHAR(64) NULL
    COMMENT 'AI版本使用的提示词契约版本，人工版本为空';
ALTER TABLE bid_chapter_version ADD CONSTRAINT uk_bid_chapter_version_unit UNIQUE (generation_unit_id);
ALTER TABLE bid_chapter_version ADD CONSTRAINT fk_bid_chapter_version_unit FOREIGN KEY (generation_unit_id)
    REFERENCES bid_generation_unit(id);

ALTER TABLE bid_reference_asset ADD COLUMN ingestion_error VARCHAR(1000) NULL
    COMMENT '素材解析失败时的已过滤错误摘要';
