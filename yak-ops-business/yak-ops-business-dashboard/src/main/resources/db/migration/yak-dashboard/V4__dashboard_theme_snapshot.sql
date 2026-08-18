-- Dashboard theme is part of the immutable version snapshot so draft, publish and restore stay consistent.
ALTER TABLE yak_dashboard_version
    ADD COLUMN theme_json JSON NULL AFTER active_dataset_id;
