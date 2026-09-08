-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-01

DELETE FROM project_detail_source
WHERE item_id IN (
    SELECT id
    FROM project_detail_item
    WHERE field_code = 'other_key_points'
);

DELETE FROM project_detail_item
WHERE field_code = 'other_key_points';
