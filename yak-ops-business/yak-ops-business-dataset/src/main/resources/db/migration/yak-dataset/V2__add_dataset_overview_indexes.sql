-- Support bounded Dataset overview counts and recent-asset reads.
ALTER TABLE yak_dataset
    ADD KEY idx_yak_dataset_update_id (update_time, id),
    ADD KEY idx_yak_dataset_create_time (create_time);
