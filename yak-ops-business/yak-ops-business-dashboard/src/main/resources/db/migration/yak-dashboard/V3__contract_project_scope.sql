-- Contract phase: empty, unresolved or mixed-project historical Dashboards must block deployment.
ALTER TABLE yak_dashboard
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';
