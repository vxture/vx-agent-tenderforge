-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-04

ALTER TABLE bid_source_file ADD COLUMN parse_stage VARCHAR(32) NOT NULL DEFAULT 'PENDING';
ALTER TABLE bid_source_file ADD COLUMN parse_progress INT NOT NULL DEFAULT 0;
ALTER TABLE bid_source_file ADD COLUMN parse_started_at TIMESTAMP NULL;
ALTER TABLE bid_source_file ADD COLUMN parse_finished_at TIMESTAMP NULL;

UPDATE bid_source_file
SET parse_stage = CASE
        WHEN parse_status = 'SUCCEEDED' THEN 'COMPLETE'
        WHEN parse_status = 'FAILED' THEN 'FAILED'
        WHEN parse_status = 'PARSING' THEN 'QUEUED'
        ELSE 'PENDING'
    END,
    parse_progress = CASE WHEN parse_status = 'SUCCEEDED' THEN 100 ELSE 0 END,
    parse_finished_at = CASE
        WHEN parse_status IN ('SUCCEEDED', 'FAILED') THEN updated_at
        ELSE NULL
    END;
