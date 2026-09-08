-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-31

ALTER TABLE generation_task
    ADD COLUMN report_mode VARCHAR(24) NULL
    COMMENT '完整报告任务模式：INITIAL首次生成、UPDATE资料更新，章节任务为空';
ALTER TABLE generation_task
    ADD COLUMN candidate_status VARCHAR(24) NULL
    COMMENT '更新候选状态：READY、APPLIED、DISCARDED，非更新任务为空';
ALTER TABLE generation_task
    ADD COLUMN candidate_slot VARCHAR(16) NULL
    COMMENT '活动候选唯一槽：ACTIVE表示当前项目待处理候选，处理后为空';
ALTER TABLE generation_task
    ADD COLUMN applied_at TIMESTAMP NULL
    COMMENT '候选采用时间，时区：UTC+8';
ALTER TABLE generation_task
    ADD COLUMN discarded_at TIMESTAMP NULL
    COMMENT '候选放弃时间，时区：UTC+8';

UPDATE generation_task
SET report_mode = 'INITIAL'
WHERE task_type = 'REPORT' AND report_mode IS NULL;

CREATE UNIQUE INDEX uk_report_candidate_slot
    ON generation_task(project_id, candidate_slot);

CREATE TABLE report_workspace (
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，每个项目仅一条正文工作状态',
    active_snapshot_id VARCHAR(36) NULL COMMENT '当前编辑稿绑定的不可变确认快照ID',
    active_task_id VARCHAR(36) NULL COMMENT '最近一次被采用的完整报告任务ID',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '正文工作状态乐观锁版本',
    updated_by VARCHAR(36) NOT NULL COMMENT '最近更新用户ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    PRIMARY KEY (project_id),
    CONSTRAINT fk_report_workspace_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_report_workspace_snapshot FOREIGN KEY (active_snapshot_id) REFERENCES project_input_snapshot(id),
    CONSTRAINT fk_report_workspace_task FOREIGN KEY (active_task_id) REFERENCES generation_task(id),
    CONSTRAINT fk_report_workspace_user FOREIGN KEY (updated_by) REFERENCES app_user(id)
) COMMENT='项目当前正文工作状态，数据来源为首次报告生成或候选采用，项目生命周期内长期保留';

INSERT INTO report_workspace(
    project_id, active_snapshot_id, active_task_id, revision, updated_by, created_at, updated_at
)
SELECT project_id, input_snapshot_id, task_id, 0, created_by, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT t.project_id, t.input_snapshot_id, t.id AS task_id, t.created_by,
           ROW_NUMBER() OVER (
               PARTITION BY t.project_id
               ORDER BY t.finished_at DESC, t.created_at DESC, t.id DESC
           ) AS row_rank
    FROM generation_task t
    WHERE t.task_type = 'REPORT'
      AND t.status = 'SUCCEEDED'
      AND t.input_snapshot_id IS NOT NULL
) ranked_report_task
WHERE row_rank = 1;

CREATE TABLE report_update_changed_field (
    task_id VARCHAR(36) NOT NULL COMMENT '更新任务ID，关联generation_task.id',
    field_code VARCHAR(80) NOT NULL COMMENT '相对当前正文快照发生有效变化的DETAIL_SHEET_V1字段编码',
    PRIMARY KEY (task_id, field_code),
    CONSTRAINT fk_update_field_task FOREIGN KEY (task_id) REFERENCES generation_task(id)
) COMMENT='正文更新任务的确认字段差异，由确定性代码计算，候选处理后长期保留';

CREATE TABLE report_update_candidate_chapter (
    task_id VARCHAR(36) NOT NULL COMMENT '更新任务ID，关联generation_task.id',
    chapter_id VARCHAR(36) NOT NULL COMMENT '受影响章节ID，关联planning_chapter.id',
    base_version_id VARCHAR(36) NOT NULL COMMENT '创建任务时当前不可变章节版本ID',
    base_revision BIGINT NOT NULL COMMENT '创建任务时章节乐观锁版本',
    candidate_version_id VARCHAR(36) NULL COMMENT 'AI生成的候选章节版本ID，任务成功前为空',
    PRIMARY KEY (task_id, chapter_id),
    CONSTRAINT fk_update_chapter_task FOREIGN KEY (task_id) REFERENCES generation_task(id),
    CONSTRAINT fk_update_chapter_chapter FOREIGN KEY (chapter_id) REFERENCES planning_chapter(id),
    CONSTRAINT fk_update_chapter_base_version FOREIGN KEY (base_version_id) REFERENCES chapter_version(id),
    CONSTRAINT fk_update_chapter_candidate_version FOREIGN KEY (candidate_version_id) REFERENCES chapter_version(id)
) COMMENT='单个正文更新候选的受影响章节及生成前版本，用于差异预览、采用冲突校验和追溯';

CREATE INDEX idx_update_candidate_task
    ON report_update_candidate_chapter(task_id, chapter_id);

ALTER TABLE export_record
    ADD COLUMN input_snapshot_id VARCHAR(36) NULL
    COMMENT '本次成果导出时当前正文绑定的不可变确认快照ID';
ALTER TABLE export_record
    ADD CONSTRAINT fk_export_input_snapshot
    FOREIGN KEY (input_snapshot_id) REFERENCES project_input_snapshot(id);
