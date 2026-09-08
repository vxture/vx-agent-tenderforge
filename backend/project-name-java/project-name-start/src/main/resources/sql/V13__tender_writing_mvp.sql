-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-08-02

CREATE TABLE bid_document (
    id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    code VARCHAR(40) NOT NULL,
    writing_method VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    target_pages INT NOT NULL DEFAULT 100,
    bidding_mode VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    workflow_step VARCHAR(24) NOT NULL DEFAULT 'SETUP',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    content_stale BOOLEAN NOT NULL DEFAULT FALSE,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_document_code UNIQUE (code),
    CONSTRAINT fk_bid_document_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
CREATE INDEX idx_bid_document_owner ON bid_document(owner_id, updated_at);

CREATE TABLE bid_source_file (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    file_size BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    parse_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    extracted_text LONGTEXT NULL,
    error_message VARCHAR(1000) NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_source_current UNIQUE (bid_id),
    CONSTRAINT fk_bid_source_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
);

CREATE TABLE bid_scoring_criterion (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    item_type VARCHAR(24) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description LONGTEXT NOT NULL,
    score DECIMAL(10,2) NULL,
    source_excerpt LONGTEXT NULL,
    sort_order INT NOT NULL,
    manually_edited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_criterion_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
);
CREATE INDEX idx_bid_criterion_order ON bid_scoring_criterion(bid_id, item_type, sort_order);

CREATE TABLE bid_reference_asset (
    id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    category VARCHAR(24) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    media_type VARCHAR(160) NOT NULL,
    file_size BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_asset_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
);
CREATE INDEX idx_bid_asset_owner ON bid_reference_asset(owner_id, category, status, updated_at);

CREATE TABLE bid_asset_selection (
    bid_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    selected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (bid_id, asset_id),
    CONSTRAINT fk_bid_selection_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_selection_asset FOREIGN KEY (asset_id) REFERENCES bid_reference_asset(id)
);

CREATE TABLE bid_outline_node (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36) NULL,
    level_no INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    planned_pages INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_outline_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_outline_parent FOREIGN KEY (parent_id) REFERENCES bid_outline_node(id)
);
CREATE INDEX idx_bid_outline_order ON bid_outline_node(bid_id, sort_order);

CREATE TABLE bid_chapter (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    outline_node_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    generation_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revision BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_chapter_outline UNIQUE (outline_node_id),
    CONSTRAINT fk_bid_chapter_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_chapter_outline FOREIGN KEY (outline_node_id) REFERENCES bid_outline_node(id)
);
CREATE INDEX idx_bid_chapter_bid ON bid_chapter(bid_id, updated_at);

CREATE TABLE bid_generation_task (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_units INT NOT NULL,
    completed_units INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bid_task_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id)
);
CREATE INDEX idx_bid_task_latest ON bid_generation_task(bid_id, created_at);

CREATE TABLE bid_export (
    id VARCHAR(36) NOT NULL,
    bid_id VARCHAR(36) NOT NULL,
    version_no INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_bid_export_version UNIQUE (bid_id, version_no),
    CONSTRAINT fk_bid_export_bid FOREIGN KEY (bid_id) REFERENCES bid_document(id),
    CONSTRAINT fk_bid_export_user FOREIGN KEY (created_by) REFERENCES app_user(id)
);
CREATE INDEX idx_bid_export_latest ON bid_export(bid_id, version_no);
