-- GENERATED_BY_AI
-- MODEL: gpt-5
-- DATE: 2026-07-31

ALTER TABLE app_user
    ADD COLUMN avatar_revision VARCHAR(36) NULL COMMENT '头像缓存版本，不包含私有存储对象键';

CREATE TABLE user_media_asset (
    id VARCHAR(36) NOT NULL COMMENT '图片素材ID，UUID，也是正文asset协议稳定引用',
    owner_id VARCHAR(36) NOT NULL COMMENT '素材所有用户ID，关联app_user.id',
    display_name VARCHAR(160) NOT NULL COMMENT '用户可搜索和重命名的素材显示名称',
    original_file_name VARCHAR(255) NOT NULL COMMENT '上传时原始文件名',
    object_key VARCHAR(500) NOT NULL COMMENT '私有文件对象键，敏感内部字段，不通过API返回',
    media_type VARCHAR(64) NOT NULL COMMENT '真实图片类型：image/png或image/jpeg',
    width_px INT NOT NULL COMMENT '原图宽度，单位：像素，范围1至12000',
    height_px INT NOT NULL COMMENT '原图高度，单位：像素，范围1至12000',
    file_size BIGINT NOT NULL COMMENT '原始文件大小，单位：字节，最大15728640',
    sha256 VARCHAR(64) NOT NULL COMMENT '文件内容SHA-256摘要',
    status VARCHAR(16) NOT NULL COMMENT '素材状态：ACTIVE=可用，REMOVED=已从素材库移除但保留历史引用',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，时区：UTC+8',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间，时区：UTC+8',
    revision BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    CONSTRAINT fk_user_media_asset_owner FOREIGN KEY (owner_id) REFERENCES app_user(id)
) COMMENT='编制员个人图片素材表，文件由用户上传，业务实时更新；软删除后长期保留以维持正文和成果引用';

CREATE INDEX idx_user_media_asset_owner
    ON user_media_asset(owner_id, status, updated_at);

