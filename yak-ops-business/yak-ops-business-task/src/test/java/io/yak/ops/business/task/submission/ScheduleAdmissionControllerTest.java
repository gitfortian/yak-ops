package io.yak.ops.business.task.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Checks scheduled admission lifecycle permits and logical identities without starting a time engine. */
class ScheduleAdmissionControllerTest {
    @Test
    void occurrenceIdentityIgnoresActualTimeAndSeparatesAmbiguousTuples() {
        Instant time = Instant.parse("2026-10-13T00:01:00Z");
        String request = ScheduleRequestId.of("ws", "schedule", 2, time);
        assertTrue(request.matches("[A-Za-z0-9_-]{8,128}"));
        assertEquals(request, ScheduleRequestId.of("ws", "schedule", 2, time));
        assertFalse(request.equals(ScheduleRequestId.of("ws", "schedule", 3, time)));
        assertFalse(ScheduleRequestId.of("ab", "c", 1, time).equals(ScheduleRequestId.of("a", "bc", 1, time)));
        assertFalse(request.equals(ScheduleRequestId.of("ws", "schedule", 2, time.plusSeconds(60))));
    }

    @Test
    void preparedOwnershipDoesNotOpenAdmissionAndBackwardClockClosesIt() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-13T00:00:00Z"));
        Clock clock = clock(now);
        ScheduleAdmissionController gate = new ScheduleAdmissionController(clock, Duration.ofSeconds(60));
        gate.prepare("owner", () -> {});
        assertThrows(IllegalStateException.class, gate::enter);
        gate.open();
        assertThrows(IllegalStateException.class, gate::checkAdmission);
        try (var ignored = gate.enter()) {
            assertEquals("owner", gate.checkAdmission());
        }
        now.set(now.get().minusSeconds(1));
        assertThrows(IllegalStateException.class, gate::enter);
        assertFalse(gate.isOpen());
    }

    @Test
    void shutdownWaitsForTheCallbackPermitWithoutAllowingAnotherAdmission() throws Exception {
        ScheduleAdmissionController gate = new ScheduleAdmissionController(Clock.systemUTC(), Duration.ofSeconds(60));
        gate.prepare("owner", () -> {});
        gate.open();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> transaction = CompletableFuture.runAsync(() -> {
            try (var ignored = gate.enter()) {
                held.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        });
        assertTrue(held.await(5, TimeUnit.SECONDS));
        gate.stop();
        assertThrows(IllegalStateException.class, gate::enter);
        CompletableFuture<Void> drained = CompletableFuture.runAsync(gate::awaitQuiescence);
        assertFalse(drained.isDone());
        release.countDown();
        transaction.get(5, TimeUnit.SECONDS);
        drained.get(5, TimeUnit.SECONDS);
    }

    @Test
    void permitCannotBeClosedByAnotherThread() throws Exception {
        ScheduleAdmissionController controller =
                new ScheduleAdmissionController(Clock.systemUTC(), Duration.ofSeconds(60));
        controller.prepare("owner", () -> {});
        controller.open();
        try (var permit = controller.enter()) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread other = Thread.ofPlatform().start(() -> {
                try {
                    permit.close();
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            other.join();
            assertTrue(failure.get() instanceof IllegalStateException);
            assertTrue(controller.isOpen());
        }
        controller.stop();
        controller.awaitQuiescence();
    }

    @Test
    void invalidLatenessIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScheduleAdmissionController(Clock.systemUTC(), Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScheduleAdmissionController(Clock.systemUTC(), Duration.ofMillis(-1)));
    }

    private static Clock clock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
