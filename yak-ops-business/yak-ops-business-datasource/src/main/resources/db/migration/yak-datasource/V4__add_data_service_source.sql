-- Track the immutable asset/revision snapshot a Data Service was published from.
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN source_type VARCHAR(64) DEFAULT NULL COMMENT '发布来源类型' AFTER description,
    ADD COLUMN source_ref VARCHAR(128) DEFAULT NULL COMMENT '发布来源引用' AFTER source_type,
    ADD COLUMN source_revision_id BIGINT DEFAULT NULL COMMENT '来源版本 ID' AFTER source_ref,
    ADD COLUMN source_revision_no INT DEFAULT NULL COMMENT '来源版本号' AFTER source_revision_id,
    ADD UNIQUE KEY uk_yak_ops_data_service_source (source_type, source_ref),
    ADD KEY idx_yak_ops_data_service_source_revision (source_revision_id);
