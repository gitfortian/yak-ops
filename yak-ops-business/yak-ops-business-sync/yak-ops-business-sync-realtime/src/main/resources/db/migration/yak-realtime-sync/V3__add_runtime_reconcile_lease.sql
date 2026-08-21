CREATE TABLE IF NOT EXISTS yak_realtime_runtime_lease (
  id TINYINT NOT NULL,
  lease_owner VARCHAR(128),
  lease_until DATETIME(3),
  update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id)
);

INSERT INTO yak_realtime_runtime_lease (id, lease_until)
VALUES (1, '1970-01-01 00:00:00.000')
ON DUPLICATE KEY UPDATE id = VALUES(id);
