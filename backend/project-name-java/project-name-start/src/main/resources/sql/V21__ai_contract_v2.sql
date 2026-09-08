-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-06

CREATE TABLE bid_source_segment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '源片段主键ID',
    source_id VARCHAR(36) NOT NULL COMMENT '招标文件ID，关联bid_source_file.id',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID，关联bid_document.id',
    sequence_no INT NOT NULL COMMENT '片段在解析结果中的顺序，从0开始',
    locator_type VARCHAR(32) NOT NULL COMMENT '定位类型：PARAGRAPH、TABLE、PAGE等',
    locator VARCHAR(300) NOT NULL COMMENT '来源位置，例如段落号、页码或表格位置',
    segment_text LONGTEXT NOT NULL COMMENT '解析出的文字或表格文本，仅用于本标书AI处理',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_source_segment_order UNIQUE (source_id, sequence_no),
    CONSTRAINT fk_bid_source_segment_source FOREIGN KEY (source_id)
        REFERENCES bid_source_file(id) ON DELETE CASCADE,
    CONSTRAINT fk_bid_source_segment_bid FOREIGN KEY (bid_id)
        REFERENCES bid_document(id)
) COMMENT='招标文件有序解析片段，文件只解析一次，对象级AI重试直接复用';
CREATE INDEX idx_bid_source_segment_bid ON bid_source_segment(bid_id, source_id, sequence_no);

ALTER TABLE bid_source_file ADD COLUMN overview_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
    COMMENT '项目概述状态：PENDING、RUNNING、SUCCEEDED、FAILED';
ALTER TABLE bid_source_file ADD COLUMN overview_content LONGTEXT NULL
    COMMENT '最近一次成功生成的项目概述Markdown';
ALTER TABLE bid_source_file ADD COLUMN overview_error_message VARCHAR(1000) NULL
    COMMENT '项目概述最近失败的安全错误摘要';
ALTER TABLE bid_source_file ADD COLUMN overview_completed_at TIMESTAMP NULL
    COMMENT '项目概述完成时间，时区UTC+8';
ALTER TABLE bid_source_file ADD COLUMN scoring_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
    COMMENT '技术评分状态：PENDING、RUNNING、SUCCEEDED、FAILED';
ALTER TABLE bid_source_file ADD COLUMN scoring_content LONGTEXT NULL
    COMMENT '最近一次成功生成的技术评分要求Markdown';
ALTER TABLE bid_source_file ADD COLUMN scoring_error_message VARCHAR(1000) NULL
    COMMENT '技术评分最近失败的安全错误摘要';
ALTER TABLE bid_source_file ADD COLUMN scoring_completed_at TIMESTAMP NULL
    COMMENT '技术评分完成时间，时区UTC+8';

UPDATE bid_source_file source
SET overview_status = CASE WHEN EXISTS(
        SELECT 1 FROM bid_scoring_criterion criterion
        WHERE criterion.bid_id = source.bid_id AND criterion.item_type = 'PROJECT_OVERVIEW'
    ) THEN 'SUCCEEDED' ELSE 'PENDING' END,
    overview_content = (
        SELECT criterion.description FROM bid_scoring_criterion criterion
        WHERE criterion.bid_id = source.bid_id AND criterion.item_type = 'PROJECT_OVERVIEW'
        ORDER BY criterion.sort_order LIMIT 1
    ),
    scoring_status = CASE WHEN EXISTS(
        SELECT 1 FROM bid_scoring_criterion criterion
        WHERE criterion.bid_id = source.bid_id AND criterion.item_type = 'TECHNICAL_SCORING'
    ) THEN 'SUCCEEDED' ELSE 'PENDING' END,
    scoring_content = (
        SELECT criterion.description FROM bid_scoring_criterion criterion
        WHERE criterion.bid_id = source.bid_id AND criterion.item_type = 'TECHNICAL_SCORING'
        ORDER BY criterion.sort_order LIMIT 1
    );

ALTER TABLE bid_ai_run ADD COLUMN object_name VARCHAR(100) NULL
    COMMENT '单次AI调用负责的业务对象名称';
ALTER TABLE bid_ai_run ADD COLUMN schema_version VARCHAR(64) NULL COMMENT '输出Schema版本';
ALTER TABLE bid_ai_run ADD COLUMN attempt_count INT NOT NULL DEFAULT 1
    COMMENT '同一幂等AI运行的实际调用尝试次数';
ALTER TABLE bid_ai_run ADD COLUMN failure_reason VARCHAR(1000) NULL
    COMMENT '字段级校验或上游失败原因，不含完整响应';
ALTER TABLE bid_ai_run ADD COLUMN finish_reason VARCHAR(32) NULL
    COMMENT '模型finish_reason，例如stop或length';
ALTER TABLE bid_ai_run ADD COLUMN response_length INT NULL COMMENT '模型原始响应字符数';
ALTER TABLE bid_ai_run ADD COLUMN response_hash CHAR(64) NULL
    COMMENT '模型原始响应SHA-256，不保存原文';
ALTER TABLE bid_ai_run ADD COLUMN response_fields VARCHAR(1000) NULL
    COMMENT '成功响应的顶层字段名，逗号分隔';
