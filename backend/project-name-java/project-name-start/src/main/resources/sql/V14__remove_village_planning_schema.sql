-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-02

DELETE FROM audit_log
WHERE target_type IN (
    'PROJECT', 'DOCUMENT', 'FACT', 'ANALYSIS', 'DECISION', 'CHAPTER',
    'REPORT', 'EXPORT', 'MEDIA_ASSET', 'ONTOLOGY'
)
OR action_code LIKE 'PROJECT_%'
OR action_code LIKE 'DOCUMENT_%'
OR action_code LIKE 'FACT_%'
OR action_code LIKE 'ANALYSIS_%'
OR action_code LIKE 'DECISION_%'
OR action_code LIKE 'CHAPTER_%'
OR action_code LIKE 'REPORT_%'
OR action_code LIKE 'EXPORT_%';

DROP INDEX idx_audit_log_project ON audit_log;
ALTER TABLE audit_log DROP COLUMN project_id;
CREATE INDEX idx_audit_log_target ON audit_log(target_type, target_id, created_at);

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS report_update_candidate_chapter;
DROP TABLE IF EXISTS report_update_changed_field;
DROP TABLE IF EXISTS report_workspace;
DROP TABLE IF EXISTS generation_task_event;
DROP TABLE IF EXISTS generation_task;
DROP TABLE IF EXISTS chapter_numeric_usage;
DROP TABLE IF EXISTS chapter_rule_usage;
DROP TABLE IF EXISTS chapter_version_warning;
DROP TABLE IF EXISTS chapter_version_placeholder;
DROP TABLE IF EXISTS chapter_version_gap;
DROP TABLE IF EXISTS chapter_version_citation;
DROP TABLE IF EXISTS chapter_version_reference;
DROP TABLE IF EXISTS chapter_version;
DROP TABLE IF EXISTS chapter_input_requirement;
DROP TABLE IF EXISTS export_chapter_snapshot;
DROP TABLE IF EXISTS export_record;
DROP TABLE IF EXISTS project_detail_source;
DROP TABLE IF EXISTS project_detail_item;
DROP TABLE IF EXISTS project_detail_sheet;
DROP TABLE IF EXISTS project_input_snapshot;
DROP TABLE IF EXISTS project_rule_snapshot_item;
DROP TABLE IF EXISTS project_rule_snapshot;
DROP TABLE IF EXISTS ontology_instance_relation;
DROP TABLE IF EXISTS ontology_relation_definition;
DROP TABLE IF EXISTS ontology_node_definition;
DROP TABLE IF EXISTS ontology_unit_definition;
DROP TABLE IF EXISTS ontology_release;
DROP TABLE IF EXISTS planning_analysis;
DROP TABLE IF EXISTS planning_rule_condition;
DROP TABLE IF EXISTS planning_rule_chapter;
DROP TABLE IF EXISTS planning_rule;
DROP TABLE IF EXISTS planning_decision;
DROP TABLE IF EXISTS planning_scenario;
DROP TABLE IF EXISTS planning_fact;
DROP TABLE IF EXISTS evidence_ref;
DROP TABLE IF EXISTS planning_document;
DROP TABLE IF EXISTS planning_chapter;
DROP TABLE IF EXISTS planning_project;
DROP TABLE IF EXISTS rule_package;
DROP TABLE IF EXISTS user_media_asset;

SET FOREIGN_KEY_CHECKS = 1;
