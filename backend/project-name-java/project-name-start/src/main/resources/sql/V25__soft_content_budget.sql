-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-28

ALTER TABLE bid_generation_unit ADD COLUMN visible_characters INT NULL
    COMMENT '模型单元正文的可见字符数，不含HTML标签和空白字符，单位：字符'
    AFTER word_budget;
ALTER TABLE bid_generation_unit ADD COLUMN budget_variance_ratio DECIMAL(8,4) NULL
    COMMENT '可见字符相对word_budget的偏差比例，(实际-预算)/预算，可为负数'
    AFTER visible_characters;
ALTER TABLE bid_generation_unit ADD COLUMN budget_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
    COMMENT '软预算状态：PENDING、WITHIN_BUDGET、OVER_BUDGET或COMPACTED'
    AFTER budget_variance_ratio;
ALTER TABLE bid_generation_unit ADD COLUMN compacted_at TIMESTAMP NULL
    COMMENT '所属章节因全文篇幅结算被接受压缩的时间，时区UTC+8'
    AFTER budget_status;

CREATE INDEX idx_bid_generation_unit_budget
    ON bid_generation_unit(task_id, budget_status, chapter_id);
