package io.yak.ops.e2e.schedule;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.e2e.support.BackendTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleIT extends BackendTestBase {
    @Test
    void realQuartzTriggerCreatesAnObservableTaskInstance() throws Exception {
        String definition = fixture.batch();
        String schedule = fixture.schedule(definition);
        String[] instance = new String[1];
        await().alias("Quartz task delivery")
                .atMost(Duration.ofSeconds(90))
                .pollInterval(Duration.ofMillis(250))
                .until(() -> {
                    var rows = fixture.api
                            .call(
                                    "POST",
                                    "/api/v1/task-instances/page",
                                    Map.of("definitionId", definition, "pageNo", 1, "pageSize", 20))
                            .path("bizData");
                    if (rows.isEmpty()) return false;
                    instance[0] = rows.get(0).path("id").asText();
                    assertEquals("SCHEDULE", rows.get(0).path("triggerType").asText());
                    assertEquals(schedule, rows.get(0).path("scheduleId").asText());
                    return true;
                });
        fixture.stopSchedule(schedule);
        fixture.track(instance[0]);
        fixture.awaitState(instance[0], "FINISHED");
        fixture.assertTarget("orders", List.of(List.of(1L, "one"), List.of(2L, "two")));
        var fires = fixture.api.call(
                "POST", "/api/v1/schedules/" + schedule + "/fires/page", Map.of("pageNo", 1, "pageSize", 20));
        assertFalse(fires.path("bizData").isEmpty());
    }
}
