ALTER TABLE yak_dev_node
    ADD COLUMN updated_by VARCHAR(128) NULL AFTER deleted;
