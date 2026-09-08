-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-02

ALTER TABLE bid_scoring_criterion ADD COLUMN source_locator VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE bid_scoring_criterion ADD COLUMN scope VARCHAR(24) NOT NULL DEFAULT 'TECHNICAL';
ALTER TABLE bid_scoring_criterion ADD COLUMN confidence VARCHAR(16) NOT NULL DEFAULT 'HIGH';

ALTER TABLE bid_outline_node ADD COLUMN task_brief LONGTEXT NULL;
ALTER TABLE bid_outline_node ADD COLUMN must_keywords_json LONGTEXT NULL;
ALTER TABLE bid_outline_node ADD COLUMN scoring_point_ids_json LONGTEXT NULL;
