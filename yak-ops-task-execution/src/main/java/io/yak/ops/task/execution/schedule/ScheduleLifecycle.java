package io.yak.ops.task.execution.schedule;

import io.yak.ops.business.schedule.ScheduleRegistrationService;
import io.yak.ops.business.task.submission.ScheduleAdmissionController;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import io.yak.ops.task.execution.owner.EmbeddedExecutionOwner;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Starts the time engine only after embedded ownership and drains admissions before releasing it. */
@Component
public class ScheduleLifecycle implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleLifecycle.class);

    @Resource
    private EmbeddedExecutionOwner owner;

    @Resource
    private Scheduler scheduler;

    @Resource
    private ScheduleAdmissionController admissionController;

    @Resource
    private ScheduleRegistrationService registrations;

    @Value("${yak.schedule.reconcile-interval:PT30S}")
    private Duration interval;

    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .daemon(true)
            .name("schedule-registration-reconcile")
            .factory());

    /** Initializes in standby, reconciles intent, then establishes the fresh admission boundary. */
    @PostConstruct
    public void start() {
        if (interval == null || interval.toMillis() < 1) {
            throw new IllegalArgumentException("Schedule reconciliation interval must be positive");
        }
        admissionController.prepare(owner.incarnation(), () -> owner.incarnation());
        try {
            scheduler.initialize();
            registrations.reconcile();
            admissionController.open();
            scheduler.start();
            poller.scheduleWithFixedDelay(
                    this::reconcile, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void reconcile() {
        try {
            admissionController.verifyOwnership();
        } catch (RuntimeException ownershipFailure) {
            admissionController.stop();
            scheduler.standby();
            return;
        }
        try {
            registrations.reconcile();
        } catch (RuntimeException failure) {
            LOG.error(
                    "Schedule registration reconciliation failed, exceptionType={}",
                    failure.getClass().getSimpleName());
        }
    }

    /** Stops new callbacks and waits for database outcomes before closing the time engine. */
    @Override
    @PreDestroy
    public void close() {
        admissionController.stop();
        poller.shutdownNow();
        try {
            scheduler.standby();
        } finally {
            admissionController.awaitQuiescence();
            scheduler.close();
        }
    }
}
