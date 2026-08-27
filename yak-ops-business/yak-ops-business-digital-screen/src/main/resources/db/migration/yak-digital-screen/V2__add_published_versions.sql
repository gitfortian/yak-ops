-- PR 2: split mutable Draft from immutable published snapshots.
CREATE TABLE IF NOT EXISTS yak_digital_screen_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    screen_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    source_revision BIGINT NOT NULL,
    name_snapshot VARCHAR(200) NOT NULL,
    description_snapshot VARCHAR(2000) NULL,
    template_id_snapshot VARCHAR(128) NOT NULL,
    template_version_snapshot INT NOT NULL DEFAULT 1,
    bindings_json JSON NOT NULL,
    published_time DATETIME(6) NOT NULL,
    create_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yak_digital_screen_version_no (screen_id, version_no),
    KEY idx_yak_digital_screen_version_time (screen_id, published_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE yak_digital_screen
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1 AFTER bindings_json,
    ADD COLUMN published_revision BIGINT NULL AFTER revision,
    ADD COLUMN published_version_id BIGINT NULL AFTER published_revision,
    ADD COLUMN published_version_no INT NOT NULL DEFAULT 0 AFTER published_version_id,
    ADD KEY idx_yak_digital_screen_published_version (published_version_id);

-- Existing PR 1 rows that were already PUBLISHED become immutable V1 snapshots.
INSERT INTO yak_digital_screen_version (
    screen_id,
    version_no,
    source_revision,
    name_snapshot,
    description_snapshot,
    template_id_snapshot,
    template_version_snapshot,
    bindings_json,
    published_time,
    create_time
)
SELECT
    id,
    1,
    revision,
    name,
    description,
    template_id,
    template_version,
    bindings_json,
    COALESCE(published_time, update_time),
    COALESCE(published_time, update_time)
FROM yak_digital_screen
WHERE status = 'PUBLISHED';

UPDATE yak_digital_screen screen
JOIN yak_digital_screen_version version
  ON version.screen_id = screen.id AND version.version_no = 1
SET screen.published_revision = screen.revision,
    screen.published_version_id = version.id,
    screen.published_version_no = 1
WHERE screen.status = 'PUBLISHED';
