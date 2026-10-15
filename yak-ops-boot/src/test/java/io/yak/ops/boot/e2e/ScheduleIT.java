package io.yak.ops.boot.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real Quartz registration, trigger admission, revisions and resource-busy skips. */
class ScheduleIT extends BackendE2ETest {
    @Test
    void scheduleRevisionsIsolationAndQuartzDelivery() {
        String task = tasks.single(false, true);
        String schedule = verifyScheduleConfiguration(task);
        api.call("PUT", "/api/v1/schedules/" + schedule,
                Map.of("revision", 1, "cronExpression", "0 0 0 1 1 ?", "timeZone", "UTC"));
        api.call("POST", "/api/v1/schedules/" + schedule + "/enable", Map.of("revision", 2));
        api.call("POST", "/api/v1/task-definitions/" + task + "/unpublish");
        JsonNode disabled = api.call("GET", "/api/v1/schedules/" + schedule);
        assertFalse(disabled.path("enabled").asBoolean());
        assertEquals(4, disabled.path("revision").asInt());
        assertEquals("TARGET_UNPUBLISHED", disabled.path("eligibilityReason").asText());
        api.call("DELETE", "/api/v1/task-definitions/" + task);
        assertEquals("TARGET_DELETED", api.call("GET", "/api/v1/schedules/" + schedule)
                .path("eligibilityReason").asText());
        api.call("DELETE", "/api/v1/schedules/" + schedule + "?revision=4");

        String realtime = tasks.single(true, false);
        JsonNode invalid = api.call("POST", "/api/v1/schedules",
                Map.of("targetType", "TASK", "targetId", realtime,
                        "cronExpression", "0 * * * * ?", "timeZone", "UTC"));
        assertFalse(invalid.path("schedulable").asBoolean());
        assertEquals("NOT_SCHEDULABLE", invalid.path("eligibilityReason").asText());
        api.call("POST", "/api/v1/schedules/" + invalid.path("id").asText() + "/enable",
                Map.of("revision", 1), 400);

        periodicTransfer();
        busySchedule(realtime);
    }

    private String verifyScheduleConfiguration(String definition) {
        String base = "/api/v1/schedules";
        Map<String, Object> spec = Map.of("targetType", "TASK", "targetId", definition,
                "cronExpression", "0 * * * * ?", "timeZone", "UTC");
        JsonNode created = api.call("POST", base, spec);
        String id = created.path("id").asText();
        assertFalse(created.path("enabled").asBoolean());
        assertEquals(1, created.path("revision").asInt());
        assertTrue(created.path("schedulable").asBoolean());
        assertEquals("APPLIED", created.path("registrationStatus").asText());
        api.call("POST", base, spec, 409);
        api.call("POST", base, Map.of("targetType", "WORKFLOW", "targetId", definition,
                "cronExpression", "0 * * * * ?", "timeZone", "UTC"), 400);
        JsonNode preview = api.call("POST", base + "/preview", Map.of("cronExpression", "0 0 8 * * ?",
                "timeZone", "Asia/Shanghai", "after", "2026-10-13T00:00:00Z"));
        assertEquals("2026-10-14T00:00:00Z", preview.path("nextFireTimes").get(0).asText());
        assertEquals(5, preview.path("nextFireTimes").size());
        api.call("POST", base + "/preview",
                Map.of("cronExpression", "0 * * * * ?", "timeZone", "Asia/Invalid"), 400);
        JsonNode page = api.call("POST", base + "/page", Map.of("targetId", definition, "pageNo", 1, "pageSize", 20));
        assertEquals(id, page.path("bizData").get(0).path("id").asText());
        Map<String, Object> selection = Map.of("targetType", "TASK", "targetIds",
                List.of(definition, "missing"), "pageNo", 1, "pageSize", 200);
        assertEquals(1, api.call("POST", base + "/page", selection)
                .path("pagination").path("total").asInt());
        api.call("PUT", base + "/" + id, Map.of("revision", 1, "cronExpression", "0 * * * * ?", "timeZone", "UTC"));
        JsonNode updated = api.call("PUT", base + "/" + id,
                Map.of("revision", 1, "cronExpression", "0 0 8 * * ?", "timeZone", "Asia/Shanghai"));
        assertEquals(2, updated.path("revision").asInt());
        api.call("PUT", base + "/" + id,
                Map.of("revision", 1, "cronExpression", "0 * * * * ?", "timeZone", "UTC"), 409);

        String workspace = api.workspace();
        String different = api.call("POST", "/api/v1/workspaces",
                Map.of("name", "schedule-" + TaskFixtures.unique())).path("id").asText();
        api.workspace(different);
        try {
            assertEquals(0, api.call("POST", base + "/page", selection).path("pagination").path("total").asInt());
            api.call("GET", base + "/" + id, null, 404);
            api.call("DELETE", base + "/" + id + "?revision=2", null, 404);
            api.call("POST", base, spec, 404);
        } finally {
            api.workspace(workspace);
        }
        api.call("DELETE", base + "/" + id + "?revision=2");
        JsonNode replacement = api.call("POST", base, spec);
        assertNotEquals(id, replacement.path("id").asText());
        assertEquals("1", env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_schedule WHERE id='"
                + id + "' AND deleted_at IS NOT NULL"));
        assertThrows(RuntimeException.class, () -> env.mysql(
                "INSERT INTO yak_ops.yak_ops_schedule "
                + "(id,workspace_id,target_type,target_id,cron_expression,time_zone,"
                + "create_time,update_time,create_by,update_by) SELECT 'duplicate-e2e-schedule',"
                + "workspace_id,target_type,target_id,cron_expression,time_zone,"
                + "create_time,update_time,create_by,update_by FROM yak_ops.yak_ops_schedule WHERE id='"
                + replacement.path("id").asText() + "'"));
        return replacement.path("id").asText();
    }

    private void periodicTransfer() {
        env.pg("CREATE TABLE public.scheduled_orders (id BIGINT PRIMARY KEY, name VARCHAR(32) NOT NULL)");
        String definition = tasks.create("scheduled",
                tasks.parameters("SINGLE_TABLE_OFFLINE", List.of("FULL"),
                        List.of(TaskFixtures.route("orders", "scheduled_orders", "APPEND"))), true, true);
        String base = "/api/v1/schedules";
        String schedule = api.call("POST", base, Map.of("targetType", "TASK", "targetId", definition,
                "cronExpression", "0/10 * * * * ?", "timeZone", "UTC")).path("id").asText();
        api.call("POST", base + "/" + schedule + "/enable", Map.of("revision", 1));
        BackendE2EEnvironment.until("Quartz registration", Duration.ofSeconds(45),
                () -> api.call("GET", base + "/" + schedule),
                value -> "APPLIED".equals(value.path("registrationStatus").asText()));
        JsonNode firings = BackendE2EEnvironment.until("Scheduled JDBC admission", Duration.ofSeconds(60),
                () -> firePage(schedule), page -> {
                    for (JsonNode row : page.path("bizData")) {
                        if ("ADMITTED".equals(row.path("outcome").asText())) return true;
                    }
                    return false;
                });
        api.call("POST", base + "/" + schedule + "/disable", Map.of("revision", 2));
        JsonNode repeated = api.call("POST", base + "/" + schedule + "/disable", Map.of("revision", 2));
        assertEquals(3, repeated.path("revision").asInt());
        JsonNode fire = null;
        for (JsonNode row : firings.path("bizData")) {
            if ("ADMITTED".equals(row.path("outcome").asText())) fire = row;
        }
        assertNotNull(fire);
        String instance = fire.path("instanceId").asText();
        JsonNode completed = tasks.await(instance, Set.of("FINISHED"));
        assertEquals("SCHEDULE", completed.path("triggerType").asText());
        assertEquals(schedule, completed.path("scheduleId").asText());
        assertEquals(2, completed.path("scheduleRevision").asInt());
        assertEquals(fire.path("scheduledTime"), completed.path("scheduledTime"));
        assertEquals("2", env.pg("SELECT COUNT(*) FROM public.scheduled_orders"));
        assertEquals("0", tasks.claimCount(instance));
        String logs = api.download("/api/v1/task-instances/" + instance + "/logs/download");
        assertTrue(logs.contains("JDBC query executed"));
        assertTrue(logs.contains("JDBC sink summary"));
        assertFalse(logs.contains(BackendE2EEnvironment.PASSWORD));
        assertEquals("0", env.mysql("SELECT COUNT(*) FROM yak_ops.YAK_QRTZ_JOB_DETAILS WHERE JOB_NAME='"
                + api.workspace() + "/" + schedule + "'"));
    }

    private JsonNode firePage(String id) {
        return api.call("POST", "/api/v1/schedules/" + id + "/fires/page", Map.of("pageNo", 1, "pageSize", 100));
    }

    private void busySchedule(String realtime) {
        String running = tasks.run(realtime, "RUNNING");
        String definition = tasks.single(false, false);
        String schedule = api.call("POST", "/api/v1/schedules", Map.of("targetType", "TASK", "targetId", definition,
                "cronExpression", "0/5 * * * * ?", "timeZone", "UTC")).path("id").asText();
        api.call("POST", "/api/v1/schedules/" + schedule + "/enable", Map.of("revision", 1));
        try {
            JsonNode skipped = BackendE2EEnvironment.until("Resource-busy Quartz fire", Duration.ofSeconds(55),
                    () -> firePage(schedule), page -> {
                        for (JsonNode row : page.path("bizData")) {
                            if ("SKIPPED".equals(row.path("outcome").asText())) return true;
                        }
                        return false;
                    });
            for (JsonNode row : skipped.path("bizData")) {
                assertEquals("SKIPPED", row.path("outcome").asText());
                assertEquals("RESOURCE_BUSY", row.path("reason").asText());
                assertTrue(row.path("instanceId").isNull() || row.path("instanceId").isMissingNode());
            }
            assertEquals("0", env.mysql("SELECT COUNT(*) FROM yak_ops.yak_ops_task_instance WHERE schedule_id='"
                    + schedule + "'"));
        } finally {
            api.call("POST", "/api/v1/schedules/" + schedule + "/disable", Map.of("revision", 2));
            api.call("POST", "/api/v1/task-instances/" + running + "/cancel");
            tasks.await(running, Set.of("CANCELED"));
            assertEquals("0", tasks.claimCount(running));
        }
    }
}
