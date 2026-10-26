package io.yak.ops.business.schedule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.submission.ScheduleAdmissionController;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.RegisteredSchedule;
import io.yak.ops.plugin.scheduler.api.ScheduleRegistration;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reconciliation changes engine registration without turning user intent or trigger history into an execution queue. */
class ScheduleRegistrationServiceTest {
    private final Instant now = Instant.parse("2026-10-13T00:00:00Z");
    private final ScheduleRegistrationServiceImpl service = new ScheduleRegistrationServiceImpl();
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final ScheduleFireRepository fires = mock(ScheduleFireRepository.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final CronService cron = mock(CronService.class);
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final ScheduleAdmissionController admissionController = new ScheduleAdmissionController(clock, Duration.ofSeconds(60));
    private final ScheduleEntity row = new ScheduleEntity();

    @BeforeEach
    void initialize() throws Exception {
        for (var entry : Map.of(
                        "schedules",
                        schedules,
                        "fires",
                        fires,
                        "scheduler",
                        scheduler,
                        "cron",
                        cron,
                        "admissionController",
                        admissionController,
                        "scheduleClock",
                        clock,
                        "retention",
                        Duration.ofDays(30))
                .entrySet()) {
            var field = ScheduleRegistrationServiceImpl.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(service, entry.getValue());
        }
        admissionController.prepare("owner", () -> {});
        row.setId("schedule");
        row.setWorkspaceId("ws");
        row.setEnabled(true);
        row.setRevision(2L);
        row.setCronExpression("0 * * * * ?");
        row.setTimeZone("UTC");
        row.setEnabledAt(LocalDateTime.ofInstant(now.minusSeconds(60), ZoneOffset.UTC));
        when(schedules.queryIncludingDeleted("ws", "schedule")).thenReturn(Optional.of(row));
        when(cron.nextFireTimes(anyString(), anyString(), any(), eq(1))).thenReturn(List.of(now.plusSeconds(60)));
    }

    @Test
    void unchangedRegistrationKeepsItsNextFireState() {
        RegisteredSchedule registered = registered();
        when(scheduler.registration("ws", "schedule")).thenReturn(Optional.of(registered));
        when(scheduler.registrations()).thenReturn(List.of(registered));
        service.synchronize("ws", "schedule");
        verify(scheduler, never()).register(any());
        var observation = service.describe(List.of(row)).get("schedule");
        assertEquals("APPLIED", observation.status());
        assertEquals(now.plusSeconds(60), observation.nextFireTime());
    }

    @Test
    void missingOrStaleRegistrationUsesCurrentIntentAndReportsErrorsSafely() {
        doThrow(new IllegalStateException("private database detail"))
                .when(scheduler)
                .register(any());
        service.synchronize("ws", "schedule");
        var observation = service.describe(List.of(row)).get("schedule");
        assertEquals("ERROR", observation.status());
        assertEquals("REGISTRATION_FAILED", observation.reason());
        assertFalse(observation.toString().contains("private database detail"));
        doNothing().when(scheduler).register(any());
        row.setEnabled(false);
        service.synchronize("ws", "schedule");
        verify(scheduler).remove("ws", "schedule");
        assertFalse(row.isEnabled());
        assertEquals("APPLIED", service.describe(List.of(row)).get("schedule").status());
    }

    @Test
    void stoppedOwnerDoesNotApplyRegistrations() {
        admissionController.stop();
        service.synchronize("ws", "schedule");
        verifyNoInteractions(scheduler);
        assertEquals("ERROR", service.describe(List.of(row)).get("schedule").status());
    }

    @Test
    void observationCannotDeadlockBehindHistoryCleanupHoldingAScheduleRow() throws Exception {
        row.setEnabled(false);
        when(schedules.scan(null, 200)).thenReturn(List.of(row));
        when(schedules.scan("schedule", 200)).thenReturn(List.of());
        CountDownLatch retiring = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
                    retiring.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return null;
                })
                .when(fires)
                .retire(eq("ws"), eq("schedule"), any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var cleanup = executor.submit(service::reconcile);
            try {
                assertTrue(retiring.await(5, TimeUnit.SECONDS));
                var observation =
                        executor.submit(() -> service.describe(List.of(row))).get(2, TimeUnit.SECONDS);
                assertEquals("APPLIED", observation.get("schedule").status());
            } finally {
                release.countDown();
            }
            cleanup.get(5, TimeUnit.SECONDS);
        }
    }

    private RegisteredSchedule registered() {
        return new RegisteredSchedule(
                new ScheduleRegistration("ws", "schedule", 2, row.getCronExpression(), "UTC", now.minusSeconds(60)),
                true,
                true,
                now.plusSeconds(60));
    }
}
