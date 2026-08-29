-- Contract phase: unresolved/orphan Analysis rows must block deployment rather than guess a Project.
ALTER TABLE yak_analysis
    MODIFY COLUMN project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID';
