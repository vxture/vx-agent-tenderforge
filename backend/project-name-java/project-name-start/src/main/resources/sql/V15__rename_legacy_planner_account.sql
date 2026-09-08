-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-02

UPDATE app_user
SET display_name = '投标编制员',
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'planner'
  AND display_name IN ('规划编制员', '村庄规划编制员');
