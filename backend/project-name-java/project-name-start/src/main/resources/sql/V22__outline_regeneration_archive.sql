-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-06

CREATE TABLE bid_outline_regeneration_archive (
    id VARCHAR(36) NOT NULL COMMENT '目录重新生成恢复快照ID',
    bid_id VARCHAR(36) NOT NULL COMMENT '标书ID',
    source_outline_version INT NOT NULL COMMENT '重新生成前的目录版本号',
    outline_json LONGTEXT NOT NULL COMMENT '旧活动目录节点JSON',
    chapter_json LONGTEXT NOT NULL COMMENT '旧活动正文JSON',
    chapter_version_json LONGTEXT NOT NULL COMMENT '旧正文版本JSON',
    generation_unit_json LONGTEXT NOT NULL COMMENT '旧正文生成单元JSON',
    review_issue_json LONGTEXT NOT NULL COMMENT '旧正文审查问题JSON',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_outline_archive_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
) COMMENT='目录重新生成前的可恢复业务快照';

CREATE INDEX idx_bid_outline_archive_bid
    ON bid_outline_regeneration_archive(bid_id, created_at);
