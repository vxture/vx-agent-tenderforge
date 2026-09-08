-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-29

CREATE TABLE app_user (
    id VARCHAR(36) NOT NULL COMMENT '用户ID，UUID',
    username VARCHAR(64) NOT NULL COMMENT '登录名，全系统唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希，敏感信息',
    display_name VARCHAR(64) NOT NULL COMMENT '界面显示姓名',
    role_code VARCHAR(24) NOT NULL COMMENT '角色：PLANNER=规划编制员，ADMIN=系统管理员',
    avatar_url VARCHAR(500) NULL COMMENT '头像地址，不存储二进制内容',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '账号是否可用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
) COMMENT='系统用户表，保存规划编制员和管理员账号，业务实时更新，账号停用后保留审计关联';

CREATE TABLE user_session (
    id VARCHAR(36) NOT NULL COMMENT '会话ID，UUID',
    user_id VARCHAR(36) NOT NULL COMMENT '用户ID，关联app_user.id',
    token_hash VARCHAR(64) NOT NULL COMMENT '会话令牌SHA-256哈希，敏感信息',
    expires_at TIMESTAMP NOT NULL COMMENT '会话失效时间，时区：UTC+8',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后访问时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_user_session_token UNIQUE (token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) COMMENT='可撤销登录会话表，数据来源：账号登录，失效会话可定期清理';

CREATE TABLE rule_package (
    id VARCHAR(36) NOT NULL COMMENT '规则包ID，UUID',
    name VARCHAR(160) NOT NULL COMMENT '规则包名称',
    region_code VARCHAR(32) NOT NULL COMMENT '适用行政区划代码',
    region_name VARCHAR(160) NOT NULL COMMENT '适用行政区划名称',
    version VARCHAR(32) NOT NULL COMMENT '规则包版本',
    effective_from DATE NOT NULL COMMENT '生效日期',
    effective_to DATE NULL COMMENT '失效日期，为空表示持续有效',
    status VARCHAR(24) NOT NULL COMMENT '状态：ACTIVE、REVISING、RETIRED、PENDING',
    template_count INT NOT NULL DEFAULT 0 COMMENT '章节模板数量，单位：个',
    term_count INT NOT NULL DEFAULT 0 COMMENT '术语数量，单位：条',
    rule_count INT NOT NULL DEFAULT 0 COMMENT '规则数量，单位：条',
    notes VARCHAR(1000) NULL COMMENT '维护说明',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_rule_package_region_version UNIQUE (region_code, version)
) COMMENT='村庄规划规则包表，数据来源：现行政策和技术标准，按规则版本生命周期保留';

CREATE TABLE planning_project (
    id VARCHAR(36) NOT NULL COMMENT '项目ID，UUID',
    code VARCHAR(40) NOT NULL COMMENT '项目业务编码，全系统唯一',
    name VARCHAR(200) NOT NULL COMMENT '项目名称',
    region VARCHAR(200) NOT NULL COMMENT '项目所在行政区',
    owner_id VARCHAR(36) NOT NULL COMMENT '项目负责人ID，关联app_user.id',
    period VARCHAR(80) NOT NULL COMMENT '规划期限',
    village_type VARCHAR(80) NOT NULL COMMENT '村庄分类',
    status VARCHAR(32) NOT NULL COMMENT '状态：PREPARING、WRITING、READY_TO_EXPORT、ARCHIVED',
    progress INT NOT NULL DEFAULT 0 COMMENT '界面参考进度，范围0至100，单位：百分比',
    rule_package_id VARCHAR(36) NULL COMMENT '当前规则包ID，关联rule_package.id',
    commissioned_by VARCHAR(200) NOT NULL COMMENT '委托单位',
    planning_scope VARCHAR(1000) NOT NULL COMMENT '规划范围文字说明',
    deadline DATE NULL COMMENT '计划完成日期',
    summary VARCHAR(1000) NOT NULL COMMENT '项目当前摘要',
    export_sequence INT NOT NULL DEFAULT 0 COMMENT '成果导出版本序号，项目内原子递增',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_planning_project_code UNIQUE (code),
    CONSTRAINT fk_planning_project_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT fk_planning_project_rule FOREIGN KEY (rule_package_id) REFERENCES rule_package(id)
) COMMENT='村庄规划项目主表，数据来源：规划编制员建项，项目归档后长期保留';
CREATE INDEX idx_planning_project_owner ON planning_project(owner_id, updated_at);

CREATE TABLE planning_document (
    id VARCHAR(36) NOT NULL COMMENT '资料ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    name VARCHAR(255) NOT NULL COMMENT '用户上传时的文件名',
    category VARCHAR(80) NOT NULL COMMENT '资料包分类',
    source VARCHAR(160) NOT NULL COMMENT '资料来源单位或方式',
    reference_year VARCHAR(16) NULL COMMENT '资料对应年份',
    parse_status VARCHAR(24) NOT NULL COMMENT '解析状态：PENDING、PARSING、SUCCEEDED、PARTIAL、FAILED',
    confidence VARCHAR(24) NOT NULL COMMENT '解析可信度：PENDING、HIGH、MEDIUM、LOW',
    summary VARCHAR(1000) NULL COMMENT '解析摘要',
    object_key VARCHAR(500) NOT NULL COMMENT '私有存储对象键，不向客户端暴露真实路径',
    content_hash VARCHAR(64) NOT NULL COMMENT '文件SHA-256，用于幂等和重复识别',
    media_type VARCHAR(160) NOT NULL COMMENT '文件MIME类型',
    file_size BIGINT NOT NULL COMMENT '文件大小，单位：字节',
    error_message VARCHAR(1000) NULL COMMENT '最近一次解析失败摘要，不包含敏感堆栈',
    extracted_fact_count INT NOT NULL DEFAULT 0 COMMENT '已提取候选事实数量，单位：条',
    uploaded_by VARCHAR(36) NOT NULL COMMENT '上传用户ID，关联app_user.id',
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT fk_planning_document_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_planning_document_user FOREIGN KEY (uploaded_by) REFERENCES app_user(id)
) COMMENT='项目资料表，保存上传元数据和解析状态，原文件随项目生命周期保留';
CREATE INDEX idx_planning_document_project ON planning_document(project_id, uploaded_at);

CREATE TABLE evidence_ref (
    id VARCHAR(36) NOT NULL COMMENT '证据片段ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    document_id VARCHAR(36) NOT NULL COMMENT '资料ID，关联planning_document.id',
    locator_type VARCHAR(24) NOT NULL COMMENT '定位类型：PAGE、SHEET_ROW、PARAGRAPH、FILE',
    locator VARCHAR(200) NOT NULL COMMENT '页码、表名行号或段落编号',
    excerpt LONGTEXT NOT NULL COMMENT '原文摘录，可能包含项目敏感信息',
    content_hash VARCHAR(64) NOT NULL COMMENT '摘录内容SHA-256',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_evidence_ref_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_evidence_ref_document FOREIGN KEY (document_id) REFERENCES planning_document(id)
) COMMENT='资料证据片段表，数据来源：文件解析，随原始资料长期保留以支持事实追溯';
CREATE INDEX idx_evidence_ref_document ON evidence_ref(document_id);

CREATE TABLE planning_fact (
    id VARCHAR(36) NOT NULL COMMENT '事实ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    field_code VARCHAR(80) NOT NULL COMMENT '事实字段稳定编码',
    field_name VARCHAR(160) NOT NULL COMMENT '事实字段显示名称',
    fact_value VARCHAR(500) NOT NULL COMMENT '事实值，保持来源原始精度',
    unit VARCHAR(40) NULL COMMENT '事实物理单位',
    status VARCHAR(24) NOT NULL COMMENT '状态：CANDIDATE、CONFIRMED、VERIFYING、CONFLICT、REJECTED、EXPIRED',
    source_ref_id VARCHAR(36) NULL COMMENT '证据片段ID，关联evidence_ref.id',
    source_name VARCHAR(255) NOT NULL COMMENT '来源文件或人工录入说明',
    source_location VARCHAR(200) NULL COMMENT '来源定位展示文本',
    confidence VARCHAR(24) NOT NULL COMMENT '可信度：HIGH、MEDIUM、LOW、PENDING',
    note VARCHAR(1000) NULL COMMENT '核实或修改说明',
    source_excerpt VARCHAR(2000) NULL COMMENT '事实对应原文短摘录',
    reference_date DATE NULL COMMENT '事实数据基准日期',
    scope VARCHAR(160) NULL COMMENT '事实统计范围',
    classification_standard VARCHAR(200) NULL COMMENT '分类或计算标准',
    calculation VARCHAR(1000) NULL COMMENT '计算过程',
    conflict_group_id VARCHAR(36) NULL COMMENT '同口径冲突分组ID',
    blocking BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否属于章节关键输入缺口',
    confirmed_by VARCHAR(36) NULL COMMENT '确认用户ID，关联app_user.id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT fk_planning_fact_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_planning_fact_evidence FOREIGN KEY (source_ref_id) REFERENCES evidence_ref(id)
) COMMENT='项目事实表，保存现状和历史事实，不保存规划意图，修改和确认操作永久审计';
CREATE INDEX idx_planning_fact_project ON planning_fact(project_id, status, field_code);

CREATE TABLE planning_scenario (
    id VARCHAR(36) NOT NULL COMMENT '规划方案ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    name VARCHAR(160) NOT NULL COMMENT '方案名称',
    version_number INT NOT NULL COMMENT '方案版本序号，项目内递增',
    status VARCHAR(24) NOT NULL COMMENT '状态：CURRENT、HISTORICAL',
    created_by VARCHAR(36) NOT NULL COMMENT '创建用户ID，关联app_user.id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_planning_scenario_version UNIQUE (project_id, version_number),
    CONSTRAINT fk_planning_scenario_project FOREIGN KEY (project_id) REFERENCES planning_project(id)
) COMMENT='项目规划方案表，当前方案和历史方案均长期保留';

CREATE TABLE planning_decision (
    id VARCHAR(36) NOT NULL COMMENT '规划决策ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    scenario_id VARCHAR(36) NOT NULL COMMENT '规划方案ID，关联planning_scenario.id',
    category VARCHAR(80) NOT NULL COMMENT '决策分类',
    name VARCHAR(160) NOT NULL COMMENT '决策名称',
    decision_value VARCHAR(1000) NOT NULL COMMENT '规划决策内容',
    status VARCHAR(24) NOT NULL COMMENT '状态：PENDING、CONFIRMED、INCOMPLETE',
    basis VARCHAR(1000) NULL COMMENT '决策依据',
    confirmed_by VARCHAR(36) NULL COMMENT '确认用户ID，关联app_user.id',
    blocking BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否属于章节关键输入缺口',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT fk_planning_decision_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_planning_decision_scenario FOREIGN KEY (scenario_id) REFERENCES planning_scenario(id)
) COMMENT='规划决策表，保存规划师确认的未来安排，与现状事实严格分离';
CREATE INDEX idx_planning_decision_project ON planning_decision(project_id, scenario_id, status);

CREATE TABLE planning_chapter (
    id VARCHAR(36) NOT NULL COMMENT '章节ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    chapter_code VARCHAR(24) NOT NULL COMMENT '章节稳定编码，如CH01',
    title VARCHAR(200) NOT NULL COMMENT '章节标题',
    objective VARCHAR(1000) NOT NULL COMMENT '章节编制目标',
    status VARCHAR(24) NOT NULL COMMENT '状态：PENDING、GENERATING、NEEDS_INPUT、COMPLETED',
    current_content LONGTEXT NOT NULL COMMENT '当前章节正文，可能包含AI辅助内容',
    word_count INT NOT NULL DEFAULT 0 COMMENT '当前正文字符数，单位：个',
    citation_count INT NOT NULL DEFAULT 0 COMMENT '当前版本引用数量，单位：条',
    fact_count INT NOT NULL DEFAULT 0 COMMENT '当前版本使用事实数量，单位：条',
    current_version_id VARCHAR(36) NULL COMMENT '当前章节版本ID，迁移后建立逻辑关联',
    updated_by VARCHAR(36) NOT NULL COMMENT '最后修改用户ID，关联app_user.id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT uk_planning_chapter_code UNIQUE (project_id, chapter_code),
    CONSTRAINT fk_planning_chapter_project FOREIGN KEY (project_id) REFERENCES planning_project(id)
) COMMENT='规划文本章节表，保存每章当前可编辑正文，项目归档后长期保留';
CREATE INDEX idx_planning_chapter_project ON planning_chapter(project_id, chapter_code);

CREATE TABLE chapter_input_requirement (
    id VARCHAR(36) NOT NULL COMMENT '章节输入要求ID，UUID',
    chapter_id VARCHAR(36) NOT NULL COMMENT '章节ID，关联planning_chapter.id',
    input_type VARCHAR(24) NOT NULL COMMENT '输入类型：FACT、DECISION',
    input_code VARCHAR(80) NOT NULL COMMENT '事实字段编码或决策名称稳定编码',
    input_name VARCHAR(160) NOT NULL COMMENT '界面显示名称',
    required BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否为必需输入',
    PRIMARY KEY (id),
    CONSTRAINT uk_chapter_input UNIQUE (chapter_id, input_type, input_code),
    CONSTRAINT fk_chapter_input_chapter FOREIGN KEY (chapter_id) REFERENCES planning_chapter(id)
) COMMENT='章节输入合同表，定义生成章节需要的事实和决策，随章节模板版本维护';

CREATE TABLE chapter_version (
    id VARCHAR(36) NOT NULL COMMENT '章节版本ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    chapter_id VARCHAR(36) NOT NULL COMMENT '章节ID，关联planning_chapter.id',
    version_number INT NOT NULL COMMENT '章节内版本序号，递增',
    content LONGTEXT NOT NULL COMMENT '该版本不可变正文快照',
    provider VARCHAR(32) NOT NULL COMMENT '生成来源：MANUAL、LOCAL、DIFY、QWEN',
    model_name VARCHAR(100) NULL COMMENT '模型名称，人工保存时为空',
    prompt_version VARCHAR(40) NULL COMMENT '提示词版本',
    missing_input_count INT NOT NULL DEFAULT 0 COMMENT '生成时缺失输入数量，单位：项',
    created_by VARCHAR(36) NOT NULL COMMENT '创建用户ID，关联app_user.id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_chapter_version UNIQUE (chapter_id, version_number),
    CONSTRAINT fk_chapter_version_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_chapter_version_chapter FOREIGN KEY (chapter_id) REFERENCES planning_chapter(id)
) COMMENT='章节正文版本表，每次生成或保存形成不可变快照，用于追溯和成果导出';

CREATE TABLE chapter_version_reference (
    id VARCHAR(36) NOT NULL COMMENT '章节引用ID，UUID',
    chapter_version_id VARCHAR(36) NOT NULL COMMENT '章节版本ID，关联chapter_version.id',
    reference_type VARCHAR(24) NOT NULL COMMENT '引用类型：FACT、DECISION、EVIDENCE、RULE',
    reference_id VARCHAR(36) NULL COMMENT '业务引用对象ID，外部规则可为空',
    label VARCHAR(255) NOT NULL COMMENT '引用显示名称',
    locator VARCHAR(255) NULL COMMENT '来源定位信息',
    PRIMARY KEY (id),
    CONSTRAINT fk_chapter_reference_version FOREIGN KEY (chapter_version_id) REFERENCES chapter_version(id)
) COMMENT='章节版本引用表，保存生成时实际使用的来源，随章节版本永久保留';

CREATE TABLE generation_task (
    id VARCHAR(36) NOT NULL COMMENT '生成任务ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    chapter_id VARCHAR(36) NOT NULL COMMENT '章节ID，关联planning_chapter.id',
    status VARCHAR(24) NOT NULL COMMENT '状态：PENDING、RUNNING、SUCCEEDED、FAILED',
    provider VARCHAR(32) NOT NULL COMMENT '请求的生成提供方：LOCAL、DIFY、QWEN',
    instruction VARCHAR(2000) NULL COMMENT '用户本次生成要求',
    error_code VARCHAR(80) NULL COMMENT '失败错误码',
    error_message VARCHAR(1000) NULL COMMENT '失败摘要，不包含密钥或敏感堆栈',
    started_at TIMESTAMP NULL COMMENT '开始时间，时区：UTC+8',
    finished_at TIMESTAMP NULL COMMENT '结束时间，时区：UTC+8',
    created_by VARCHAR(36) NOT NULL COMMENT '发起用户ID，关联app_user.id',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT fk_generation_task_project FOREIGN KEY (project_id) REFERENCES planning_project(id),
    CONSTRAINT fk_generation_task_chapter FOREIGN KEY (chapter_id) REFERENCES planning_chapter(id)
) COMMENT='章节生成任务表，记录本地或外部AI调用状态，保留用于排障和审计';

CREATE TABLE export_record (
    id VARCHAR(36) NOT NULL COMMENT '成果版本ID，UUID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    version_number INT NOT NULL COMMENT '项目内成果版本序号',
    version_label VARCHAR(16) NOT NULL COMMENT '显示版本号，如V01',
    format VARCHAR(16) NOT NULL COMMENT '成果格式：DOCX',
    status VARCHAR(24) NOT NULL COMMENT '状态：EXPORTING、COMPLETED、FAILED',
    note VARCHAR(500) NOT NULL COMMENT '版本说明',
    object_key VARCHAR(500) NULL COMMENT '完成文件的私有存储对象键',
    file_name VARCHAR(255) NULL COMMENT '下载文件名',
    file_size BIGINT NULL COMMENT '成果文件大小，单位：字节',
    error_message VARCHAR(1000) NULL COMMENT '导出失败摘要',
    generated_by VARCHAR(36) NOT NULL COMMENT '导出用户ID，关联app_user.id',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导出时间，时区：UTC+8',
    PRIMARY KEY (id),
    CONSTRAINT uk_export_record_version UNIQUE (project_id, version_number),
    CONSTRAINT fk_export_record_project FOREIGN KEY (project_id) REFERENCES planning_project(id)
) COMMENT='规划文本成果版本表，每次导出形成不可覆盖记录，项目归档后长期保留';
CREATE INDEX idx_export_record_project ON export_record(project_id, version_number);

CREATE TABLE export_chapter_snapshot (
    id VARCHAR(36) NOT NULL COMMENT '导出章节快照ID，UUID',
    export_id VARCHAR(36) NOT NULL COMMENT '成果版本ID，关联export_record.id',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID，关联planning_project.id',
    chapter_id VARCHAR(36) NOT NULL COMMENT '原章节ID，关联planning_chapter.id',
    chapter_code VARCHAR(24) NOT NULL COMMENT '导出时章节编码',
    title VARCHAR(200) NOT NULL COMMENT '导出时章节标题',
    content LONGTEXT NOT NULL COMMENT '导出时不可变章节正文',
    PRIMARY KEY (id),
    CONSTRAINT uk_export_chapter UNIQUE (export_id, chapter_code),
    CONSTRAINT fk_export_snapshot_export FOREIGN KEY (export_id) REFERENCES export_record(id),
    CONSTRAINT fk_export_snapshot_project FOREIGN KEY (project_id) REFERENCES planning_project(id)
) COMMENT='成果章节快照表，确保历史文件不受当前正文修改影响，随成果版本永久保留';

CREATE TABLE audit_log (
    id VARCHAR(36) NOT NULL COMMENT '审计日志ID，UUID',
    user_id VARCHAR(36) NULL COMMENT '操作用户ID，系统任务可为空',
    project_id VARCHAR(36) NULL COMMENT '项目ID，非项目操作可为空',
    action_code VARCHAR(64) NOT NULL COMMENT '操作编码，如PROJECT_CREATE、CHAPTER_GENERATE、EXPORT_DOWNLOAD',
    target_type VARCHAR(64) NOT NULL COMMENT '操作对象类型',
    target_id VARCHAR(64) NULL COMMENT '操作对象ID',
    result_code VARCHAR(24) NOT NULL COMMENT '结果：SUCCESS、FAILED、DENIED',
    detail_summary VARCHAR(1000) NULL COMMENT '操作摘要，不保存密码、令牌、密钥和完整个人信息',
    trace_id VARCHAR(64) NOT NULL COMMENT '请求链路ID',
    ip_address VARCHAR(64) NULL COMMENT '客户端IP，属于个人信息，受访问控制',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间，时区：UTC+8',
    PRIMARY KEY (id)
) COMMENT='业务审计日志表，记录关键写操作和下载行为，默认长期保留并限制管理员访问';
CREATE INDEX idx_audit_log_project ON audit_log(project_id, created_at);
CREATE INDEX idx_audit_log_user ON audit_log(user_id, created_at);
