package io.yak.ops.task.execution.schedule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.schedule.ScheduleRegistrationService;
import io.yak.ops.business.task.submission.ScheduleAdmissionGate;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies owner-before-store startup and gate-before-store shutdown without pretending to run Quartz. */
class ScheduleLifecycleTest {
    @Test
    void ownershipAndReconciliationPrecedeAdmissionAndEngineStart() throws Exception {
        EmbeddedExecutionOwner owner = mock(EmbeddedExecutionOwner.class);
        Scheduler scheduler = mock(Scheduler.class);
        ScheduleRegistrationService registrations = mock(ScheduleRegistrationService.class);
        ScheduleAdmissionGate gate = new ScheduleAdmissionGate(Clock.systemUTC(), Duration.ofSeconds(60));
        ScheduleLifecycle lifecycle = setup(owner, scheduler, gate, registrations);
        when(owner.incarnation()).thenReturn("owner");
        doAnswer(call -> {
                    assertFalse(gate.isOpen());
                    return null;
                })
                .when(scheduler)
                .initialize();
        doAnswer(call -> {
                    assertFalse(gate.isOpen());
                    return null;
                })
                .when(registrations)
                .reconcile();
        doAnswer(call -> {
                    assertTrue(gate.isOpen());
                    return null;
                })
                .when(scheduler)
                .start();
        doAnswer(call -> {
                    assertFalse(gate.isOpen());
                    return null;
                })
                .when(scheduler)
                .standby();
        try {
            lifecycle.start();
            var order = inOrder(scheduler, registrations);
            order.verify(scheduler).initialize();
            order.verify(registrations).reconcile();
            order.verify(scheduler).start();
        } finally {
            lifecycle.close();
        }
        assertFalse(gate.isOpen());
    }

    @Test
    void losingOwnerNeverInitializesTheActiveStore() throws Exception {
        EmbeddedExecutionOwner owner = mock(EmbeddedExecutionOwner.class);
        Scheduler scheduler = mock(Scheduler.class);
        ScheduleRegistrationService registrations = mock(ScheduleRegistrationService.class);
        ScheduleAdmissionGate gate = new ScheduleAdmissionGate(Clock.systemUTC(), Duration.ofSeconds(60));
        ScheduleLifecycle lifecycle = setup(owner, scheduler, gate, registrations);
        when(owner.incarnation()).thenThrow(new IllegalStateException("owner unavailable"));
        assertThrows(IllegalStateException.class, lifecycle::start);
        verifyNoInteractions(scheduler, registrations);
        lifecycle.close();
    }

    private ScheduleLifecycle setup(
            EmbeddedExecutionOwner owner,
            Scheduler scheduler,
            ScheduleAdmissionGate gate,
            ScheduleRegistrationService registrations)
            throws Exception {
        ScheduleLifecycle lifecycle = new ScheduleLifecycle();
        for (var entry : Map.of(
                        "owner",
                        owner,
                        "scheduler",
                        scheduler,
                        "gate",
                        gate,
                        "registrations",
                        registrations,
                        "interval",
                        Duration.ofDays(1))
                .entrySet()) {
            var field = ScheduleLifecycle.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(lifecycle, entry.getValue());
        }
        return lifecycle;
    }
}
