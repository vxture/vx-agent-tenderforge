-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-05

CREATE TABLE bid_outline_task (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    stage VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    progress INT NOT NULL DEFAULT 0,
    input_revision BIGINT NOT NULL,
    workflow_run_id VARCHAR(128) NULL,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_outline_task_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
);
CREATE INDEX idx_bid_outline_task_latest ON bid_outline_task(bid_id, created_at);
