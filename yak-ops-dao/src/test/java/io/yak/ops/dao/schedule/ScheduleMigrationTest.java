package io.yak.ops.dao.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Checks schedule and Quartz constraints in the final fresh-install schema. */
class ScheduleMigrationTest {
    @Test
    void createsSchedulesWithRequiredConstraintsAndQuartzIndexes() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream("/db/migration/yak-ops/V1__baseline.sql")) {
            assertNotNull(stream);
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(sql.contains("UNIQUE KEY uk_ops_schedule_target (workspace_id, target_type, active_target_id)"));
        assertTrue(sql.contains("CASE WHEN deleted_at IS NULL THEN target_id ELSE NULL END"));
        assertTrue(sql.contains("ck_ops_schedule_authority"));
        assertTrue(sql.contains("ck_ops_task_schedule_provenance"));
        assertTrue(sql.contains("uk_ops_schedule_fire_occurrence"));
        assertTrue(sql.contains("IDX_YAK_QRTZ_FT_TG"));
        assertEquals(
                11,
                Pattern.compile("(?m)^CREATE TABLE YAK_QRTZ_").matcher(sql).results().count());
    }
}
