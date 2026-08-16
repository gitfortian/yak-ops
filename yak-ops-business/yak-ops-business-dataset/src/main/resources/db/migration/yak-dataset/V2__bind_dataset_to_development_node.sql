-- Phase 3: a Dataset DAG node owns one stable Dataset identity.
-- Legacy SQL release publications keep this column NULL so both paths can coexist during migration.
ALTER TABLE yak_dataset
    ADD COLUMN development_node_id BIGINT NULL AFTER id,
    ADD UNIQUE KEY uk_yak_dataset_development_node (development_node_id);
