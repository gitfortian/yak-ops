-- Project Space expand and deterministic ownership proof for Dashboard roots.
-- DashboardVersion and all composition rows continue to inherit through dashboard_id.
ALTER TABLE yak_dashboard
    ADD COLUMN project_id BIGINT NULL COMMENT 'Yak Security Project ID' AFTER id,
    ADD KEY idx_yak_dashboard_project_update (project_id, update_time, id);

-- One evidence row is recorded for every explicit Dataset/Analysis reference.
-- A Dashboard is backfilled only when every reference resolves and all references agree.
CREATE TEMPORARY TABLE tmp_yak_dashboard_project_evidence (
    dashboard_id BIGINT NOT NULL,
    project_id BIGINT NULL
);

INSERT INTO tmp_yak_dashboard_project_evidence (dashboard_id, project_id)
SELECT v.dashboard_id, d.project_id
FROM yak_dashboard_version v
LEFT JOIN yak_dataset d ON d.id = v.active_dataset_id
WHERE v.active_dataset_id IS NOT NULL;

INSERT INTO tmp_yak_dashboard_project_evidence (dashboard_id, project_id)
SELECT v.dashboard_id, a.project_id
FROM yak_dashboard_version v
JOIN yak_dashboard_widget w ON w.dashboard_version_id = v.id
LEFT JOIN yak_analysis a ON a.id = w.analysis_id
WHERE w.analysis_id IS NOT NULL;

INSERT INTO tmp_yak_dashboard_project_evidence (dashboard_id, project_id)
SELECT v.dashboard_id, d.project_id
FROM yak_dashboard_version v
JOIN yak_dashboard_widget w ON w.dashboard_version_id = v.id
LEFT JOIN yak_dataset d
  ON d.id = CASE
      WHEN JSON_UNQUOTE(JSON_EXTRACT(w.inline_analysis_json, '$.datasetId'))
          REGEXP '^[1-9][0-9]*$'
      THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(w.inline_analysis_json, '$.datasetId')) AS UNSIGNED)
      ELSE NULL
  END
WHERE w.inline_analysis_json IS NOT NULL
  AND JSON_EXTRACT(w.inline_analysis_json, '$.datasetId') IS NOT NULL;

UPDATE yak_dashboard dashboard
JOIN (
    SELECT dashboard_id, MIN(project_id) AS project_id
    FROM tmp_yak_dashboard_project_evidence
    GROUP BY dashboard_id
    HAVING COUNT(*) = COUNT(project_id)
       AND MIN(project_id) = MAX(project_id)
) owner ON owner.dashboard_id = dashboard.id
SET dashboard.project_id = owner.project_id
WHERE dashboard.project_id IS NULL;

DROP TEMPORARY TABLE tmp_yak_dashboard_project_evidence;
