-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-30

UPDATE planning_project project
SET business_stage = CASE
    WHEN EXISTS (
        SELECT 1 FROM export_record export
        WHERE export.project_id = project.id AND export.status = 'COMPLETED'
    ) THEN 'EXPORTED'
    WHEN EXISTS (
        SELECT 1 FROM generation_task task
        WHERE task.project_id = project.id
          AND task.task_type = 'REPORT'
          AND task.status IN ('PENDING', 'RUNNING')
    ) THEN 'GENERATING'
    WHEN EXISTS (
        SELECT 1 FROM planning_chapter chapter
        WHERE chapter.project_id = project.id AND chapter.current_content <> ''
    ) THEN 'DRAFT_READY'
    WHEN EXISTS (
        SELECT 1 FROM planning_document document
        WHERE document.project_id = project.id
          AND document.parse_status IN ('PENDING', 'PARSING')
    ) THEN 'PARSING'
    WHEN EXISTS (
        SELECT 1 FROM planning_document document
        WHERE document.project_id = project.id
    ) THEN 'NEEDS_CONFIRMATION'
    ELSE 'EMPTY'
END;
