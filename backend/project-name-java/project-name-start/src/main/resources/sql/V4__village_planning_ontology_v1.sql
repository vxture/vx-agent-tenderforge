-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-29

ALTER TABLE planning_project
    ADD COLUMN ontology_version VARCHAR(64) NOT NULL DEFAULT 'SHAANXI_TEXT_ONTOLOGY_V1';

CREATE TABLE ontology_release (
    version_code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    jurisdiction_code VARCHAR(32) NOT NULL,
    scope_text VARCHAR(500) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL COMMENT 'PILOT、RELEASED、RETIRED',
    professional_review_status VARCHAR(32) NOT NULL COMMENT 'REVIEW_REQUIRED、APPROVED、REJECTED',
    reviewed_by VARCHAR(160) NULL,
    reviewed_at TIMESTAMP NULL,
    review_note VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version_code)
) COMMENT='版本化正文业务本体发布记录；软件可执行不等于专业语义已审核';

CREATE TABLE ontology_unit_definition (
    ontology_version VARCHAR(64) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    unit_name VARCHAR(200) NOT NULL,
    objective VARCHAR(1000) NOT NULL,
    material_categories VARCHAR(1000) NOT NULL,
    sequence_no INT NOT NULL,
    PRIMARY KEY (ontology_version, unit_code),
    CONSTRAINT fk_ontology_unit_release FOREIGN KEY (ontology_version)
        REFERENCES ontology_release(version_code)
) COMMENT='本体业务闭环单元定义';

CREATE TABLE ontology_node_definition (
    ontology_version VARCHAR(64) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    node_type VARCHAR(32) NOT NULL COMMENT 'FACT、ANALYSIS、DECISION、GENERATION_UNIT',
    node_code VARCHAR(100) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    evidence_policy VARCHAR(48) NOT NULL,
    gap_code VARCHAR(120) NOT NULL,
    gap_type VARCHAR(24) NOT NULL,
    gap_marker VARCHAR(500) NOT NULL,
    chapter_codes VARCHAR(100) NULL,
    sequence_no INT NOT NULL,
    PRIMARY KEY (ontology_version, unit_code, node_type, node_code),
    CONSTRAINT uk_ontology_gap_code UNIQUE (ontology_version, gap_code),
    CONSTRAINT fk_ontology_node_unit FOREIGN KEY (ontology_version, unit_code)
        REFERENCES ontology_unit_definition(ontology_version, unit_code)
) COMMENT='本体单元中的事实、分析、方案和正文出口定义';

CREATE TABLE ontology_relation_definition (
    ontology_version VARCHAR(64) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_code VARCHAR(100) NOT NULL,
    relation_type VARCHAR(32) NOT NULL COMMENT 'SUPPORTS、JUSTIFIES、CONSUMED_BY',
    target_type VARCHAR(32) NOT NULL,
    target_code VARCHAR(100) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (
        ontology_version, unit_code, source_type, source_code,
        relation_type, target_type, target_code
    ),
    CONSTRAINT fk_ontology_relation_unit FOREIGN KEY (ontology_version, unit_code)
        REFERENCES ontology_unit_definition(ontology_version, unit_code)
) COMMENT='本体类型级有向关系，用于确认门槛和闭环校验';

CREATE TABLE planning_analysis (
    id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    ontology_version VARCHAR(64) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    analysis_code VARCHAR(100) NOT NULL,
    analysis_name VARCHAR(200) NOT NULL,
    conclusion LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'DRAFT、CONFIRMED、NEEDS_RECONFIRMATION',
    confirmed_by VARCHAR(36) NULL,
    confirmed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_analysis UNIQUE (project_id, analysis_code),
    CONSTRAINT fk_planning_analysis_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_planning_analysis_user FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
) COMMENT='由规划编制员确认的专业分析判断，不保存模型自行确认的结论';
CREATE INDEX idx_planning_analysis_project
    ON planning_analysis(project_id, unit_code, status);

CREATE TABLE ontology_instance_relation (
    id VARCHAR(36) NOT NULL,
    project_id VARCHAR(36) NOT NULL,
    ontology_version VARCHAR(64) NOT NULL,
    unit_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(160) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(160) NOT NULL,
    confirmed_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_ontology_instance_relation UNIQUE (
        project_id, unit_code, source_type, source_id,
        relation_type, target_type, target_id
    ),
    CONSTRAINT fk_ontology_instance_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_ontology_instance_user FOREIGN KEY (confirmed_by) REFERENCES app_user(id)
) COMMENT='项目事实到分析、分析到方案的人工确认实例关系';
CREATE INDEX idx_ontology_relation_target
    ON ontology_instance_relation(project_id, target_type, target_id);
CREATE INDEX idx_ontology_relation_source
    ON ontology_instance_relation(project_id, source_type, source_id);

INSERT INTO ontology_release(
    version_code, name, jurisdiction_code, scope_text, lifecycle_status,
    professional_review_status, reviewed_by, reviewed_at, review_note
) VALUES (
    'SHAANXI_TEXT_ONTOLOGY_V1', '陕西村庄规划正文业务本体 V1', '610000',
    '陕西省实用性村庄规划备案版正文辅助编撰', 'PILOT', 'REVIEW_REQUIRED',
    NULL, NULL, '软件结构可执行，业务语义须由陕西村庄规划专业人员复核后方可转为正式发布。'
);

INSERT INTO ontology_unit_definition VALUES
    ('SHAANXI_TEXT_ONTOLOGY_V1', 'OU01_SCOPE', '规划适用范围与规划单元',
     '从项目范围和城镇开发边界关系形成规程适用性判断、编制任务及总则正文。',
     '项目与上位要求', 1),
    ('SHAANXI_TEXT_ONTOLOGY_V1', 'OU02_PERIOD', '规划期限与上位衔接',
     '以上位规划期限为依据，由规划编制员确认本项目期限并形成总则期限段落。',
     '项目与上位要求', 2),
    ('SHAANXI_TEXT_ONTOLOGY_V1', 'OU03_CLASSIFICATION', '村庄分类与发展定位',
     '依据正式分类成果形成分类适用性判断，由规划编制员确认村庄类型和发展定位。',
     '项目与上位要求', 3),
    ('SHAANXI_TEXT_ONTOLOGY_V1', 'OU04_ISSUE_STRATEGY', '核心资源问题与发展策略',
     '依据已确认的资源和问题事实形成问题诊断，并将目标和策略对应到具体问题。',
     '人口经济与产业|自然资源与生态环境|土地利用与历史文化|设施现状与村民需求', 4);

INSERT INTO ontology_node_definition VALUES
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','FACT','outside_urban_boundary','是否位于城镇开发边界外',TRUE,'PROJECT_INPUT_OR_DOCUMENT','ONTOLOGY_OU01_OUTSIDE_BOUNDARY','SOURCE','【待核实-依据：项目与城镇开发边界的关系】',NULL,1),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','FACT','planning_scope','规划单元与范围文字',TRUE,'PROJECT_INPUT_OR_DOCUMENT','ONTOLOGY_OU01_PLANNING_SCOPE','DATA','【待补充-数据：规划单元与范围文字】',NULL,2),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','ANALYSIS','scope_applicability_conclusion','规程适用性与规划单元判断',TRUE,'CONFIRMED_FACT_RELATIONS','ONTOLOGY_OU01_SCOPE_ANALYSIS','DATA','【待补充-数据：规程适用性与规划单元判断】',NULL,3),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','DECISION','compilation_task','本项目编制任务',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU01_COMPILATION_TASK','DECISION','【待确认-方案：本项目编制任务】',NULL,4),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','GENERATION_UNIT','GU_CH01_SCOPE','总则中的适用范围、规划单元和编制任务段落',TRUE,'CLOSED_ONTOLOGY_UNIT','ONTOLOGY_OU01_GENERATION','DATA','【待补充-数据：规划范围闭环输入】','CH01',5),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','FACT','upper_plan_period','上位规划期限',TRUE,'DOCUMENT_REQUIRED','ONTOLOGY_OU02_UPPER_PERIOD','SOURCE','【待核实-依据：现行上位规划期限】',NULL,1),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','ANALYSIS','period_alignment_conclusion','规划期限衔接判断',TRUE,'CONFIRMED_FACT_RELATIONS','ONTOLOGY_OU02_PERIOD_ANALYSIS','DATA','【待补充-数据：本项目期限与上位规划衔接判断】',NULL,2),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','DECISION','planning_period','本项目规划期限',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU02_PLANNING_PERIOD','DECISION','【待确认-方案：本项目规划期限】',NULL,3),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','GENERATION_UNIT','GU_CH01_PERIOD','总则中的规划期限与上位衔接段落',TRUE,'CLOSED_ONTOLOGY_UNIT','ONTOLOGY_OU02_GENERATION','SOURCE','【待核实-依据：规划期限闭环输入】','CH01',4),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','FACT','village_classification_result','上位成果确定的村庄分类',TRUE,'DOCUMENT_REQUIRED','ONTOLOGY_OU03_CLASSIFICATION_RESULT','SOURCE','【待核实-依据：县域村庄分类成果】',NULL,1),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','FACT','village_classification_basis','村庄分类来源与判定依据',TRUE,'DOCUMENT_REQUIRED','ONTOLOGY_OU03_CLASSIFICATION_BASIS','SOURCE','【待核实-依据：村庄分类来源与判定依据】',NULL,2),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','ANALYSIS','classification_rationale','分类适用性与发展方向判断',TRUE,'CONFIRMED_FACT_RELATIONS','ONTOLOGY_OU03_CLASSIFICATION_ANALYSIS','DATA','【待补充-数据：村庄分类适用性判断】',NULL,3),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','DECISION','village_type','本项目采用的村庄类型',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU03_VILLAGE_TYPE','DECISION','【待确认-方案：本项目采用的村庄类型】',NULL,4),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','DECISION','development_positioning','村庄发展定位',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU03_POSITIONING','DECISION','【待确认-方案：村庄发展定位】',NULL,5),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','GENERATION_UNIT','GU_CH03_POSITIONING','目标定位章的分类论证和发展定位段落',TRUE,'CLOSED_ONTOLOGY_UNIT','ONTOLOGY_OU03_GENERATION','DATA','【待补充-数据：村庄分类与发展定位闭环输入】','CH03',6),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','FACT','core_resource_summary','核心资源条件清单',TRUE,'DOCUMENT_REQUIRED','ONTOLOGY_OU04_RESOURCES','DATA','【待补充-数据：本村核心资源条件】',NULL,1),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','FACT','core_problem_summary','核心问题客观事实清单',TRUE,'DOCUMENT_REQUIRED','ONTOLOGY_OU04_PROBLEMS','DATA','【待补充-数据：本村核心问题客观事实】',NULL,2),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','ANALYSIS','core_issue_diagnosis','核心矛盾、成因与影响判断',TRUE,'CONFIRMED_FACT_RELATIONS','ONTOLOGY_OU04_DIAGNOSIS','DATA','【待补充-数据：核心问题成因与影响判断】',NULL,3),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','issue_priority','现状问题优先级',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU04_ISSUE_PRIORITY','DECISION','【待确认-方案：现状问题优先级】',NULL,4),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','development_goals','发展目标',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU04_GOALS','DECISION','【待确认-方案：发展目标】',NULL,5),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','development_strategy','发展策略',TRUE,'CONFIRMED_ANALYSIS_RELATION','ONTOLOGY_OU04_STRATEGY','DECISION','【待确认-方案：发展策略】',NULL,6),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','GENERATION_UNIT','GU_CH02_ISSUES','基础分析章的问题诊断段落',TRUE,'CLOSED_ONTOLOGY_UNIT','ONTOLOGY_OU04_CH02_GENERATION','DATA','【待补充-数据：核心问题诊断闭环输入】','CH02',7),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','GENERATION_UNIT','GU_CH03_STRATEGY','目标定位章的发展目标与策略段落',TRUE,'CLOSED_ONTOLOGY_UNIT','ONTOLOGY_OU04_CH03_GENERATION','DECISION','【待确认-方案：发展目标与策略闭环输入】','CH03',8);

INSERT INTO ontology_relation_definition VALUES
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','FACT','outside_urban_boundary','SUPPORTS','ANALYSIS','scope_applicability_conclusion',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','FACT','planning_scope','SUPPORTS','ANALYSIS','scope_applicability_conclusion',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','ANALYSIS','scope_applicability_conclusion','JUSTIFIES','DECISION','compilation_task',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU01_SCOPE','DECISION','compilation_task','CONSUMED_BY','GENERATION_UNIT','GU_CH01_SCOPE',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','FACT','upper_plan_period','SUPPORTS','ANALYSIS','period_alignment_conclusion',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','ANALYSIS','period_alignment_conclusion','JUSTIFIES','DECISION','planning_period',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU02_PERIOD','DECISION','planning_period','CONSUMED_BY','GENERATION_UNIT','GU_CH01_PERIOD',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','FACT','village_classification_result','SUPPORTS','ANALYSIS','classification_rationale',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','FACT','village_classification_basis','SUPPORTS','ANALYSIS','classification_rationale',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','ANALYSIS','classification_rationale','JUSTIFIES','DECISION','village_type',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','ANALYSIS','classification_rationale','JUSTIFIES','DECISION','development_positioning',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','DECISION','village_type','CONSUMED_BY','GENERATION_UNIT','GU_CH03_POSITIONING',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU03_CLASSIFICATION','DECISION','development_positioning','CONSUMED_BY','GENERATION_UNIT','GU_CH03_POSITIONING',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','FACT','core_resource_summary','SUPPORTS','ANALYSIS','core_issue_diagnosis',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','FACT','core_problem_summary','SUPPORTS','ANALYSIS','core_issue_diagnosis',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','ANALYSIS','core_issue_diagnosis','JUSTIFIES','DECISION','issue_priority',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','ANALYSIS','core_issue_diagnosis','JUSTIFIES','DECISION','development_goals',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','ANALYSIS','core_issue_diagnosis','JUSTIFIES','DECISION','development_strategy',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','issue_priority','CONSUMED_BY','GENERATION_UNIT','GU_CH02_ISSUES',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','development_goals','CONSUMED_BY','GENERATION_UNIT','GU_CH03_STRATEGY',TRUE),
    ('SHAANXI_TEXT_ONTOLOGY_V1','OU04_ISSUE_STRATEGY','DECISION','development_strategy','CONSUMED_BY','GENERATION_UNIT','GU_CH03_STRATEGY',TRUE);

