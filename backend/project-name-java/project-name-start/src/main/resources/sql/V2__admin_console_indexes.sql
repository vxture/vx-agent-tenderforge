-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-29

CREATE INDEX idx_generation_task_status_created
    ON generation_task(status, created_at);

CREATE INDEX idx_audit_log_action_result_created
    ON audit_log(action_code, result_code, created_at);
