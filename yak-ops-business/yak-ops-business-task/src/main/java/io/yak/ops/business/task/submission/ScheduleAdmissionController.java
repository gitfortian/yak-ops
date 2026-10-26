package io.yak.ops.business.task.submission;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Fences new scheduled admissions and drains transactions before process ownership is released. */
public final class ScheduleAdmissionController {
    private final Clock clock;
    private final Duration maxLateness;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private volatile boolean owned;
    private volatile boolean open;
    private String incarnation;
    private Runnable ownershipCheck;
    private Instant activation;
    private Instant observed;

    /**
     * Creates a closed gate with a shared injectable UTC clock.
     * @param clock authoritative owner clock
     * @param maxLateness maximum permitted admission delay
     */
    public ScheduleAdmissionController(Clock clock, Duration maxLateness) {
        this.clock = Objects.requireNonNull(clock);
        this.maxLateness = Objects.requireNonNull(maxLateness);
        if (maxLateness.isNegative() || maxLateness.isZero() || maxLateness.toMillis() < 1) {
            throw new IllegalArgumentException("Schedule lateness must be at least one millisecond");
        }
    }

    /**
     * Binds verified process ownership while new admissions remain closed.
     * @param incarnation verified database owner incarnation
     * @param ownershipCheck nonblocking verification of the held process lock
     */
    public synchronized void prepare(String incarnation, Runnable ownershipCheck) {
        if (owned) {
            throw new IllegalStateException("Schedule ownership has already been prepared");
        }
        this.incarnation = Objects.requireNonNull(incarnation);
        this.ownershipCheck = Objects.requireNonNull(ownershipCheck);
        ownershipCheck.run();
        observed = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        owned = true;
        open = false;
    }

    /** Opens admissions at a fresh boundary after initial registration reconciliation. */
    public synchronized void open() {
        verifyOwnership();
        activation = now();
        open = true;
    }

    /** Closes new registration/admission immediately; already-held permits still drain. */
    public synchronized void stop() {
        open = false;
        owned = false;
    }

    /** Waits until every admitted callback has finished its physical database transaction. */
    public void awaitQuiescence() {
        lock.writeLock().lock();
        lock.writeLock().unlock();
    }

    /** @return whether new scheduled callbacks can enter */
    public boolean isOpen() {
        return open;
    }

    /** @return maximum admitted lateness shared with Quartz misfire handling */
    public Duration maxLateness() {
        return maxLateness;
    }

    /** @return current owner's exclusive activation boundary */
    public synchronized Instant activation() {
        return activation;
    }

    /**
     * Rechecks the physical owner without opening admission during startup reconciliation.
     * @return verified owner incarnation
     */
    public synchronized String verifyOwnership() {
        if (!owned) {
            throw new IllegalStateException("Schedule execution ownership is unavailable");
        }
        try {
            ownershipCheck.run();
            now();
            return incarnation;
        } catch (RuntimeException failure) {
            stop();
            throw failure;
        }
    }

    /**
     * Reads the owner clock and suspends scheduling on backward movement.
     * @return UTC instant at millisecond precision
     */
    public synchronized Instant now() {
        Instant current = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (observed != null && current.isBefore(observed)) {
            stop();
            throw new IllegalStateException("Schedule clock moved backward; ownership must be re-established");
        }
        observed = current;
        return current;
    }

    /**
     * Holds a callback permit through commit or rollback, including uncertain commit responses.
     * @return permit to close only after the transaction completes
     */
    public Permit enter() {
        lock.readLock().lock();
        try {
            checkAdmission();
            return new Permit();
        } catch (RuntimeException failure) {
            lock.readLock().unlock();
            throw failure;
        }
    }

    /** @return verified incarnation for a final admission holding a callback permit */
    public String checkAdmission() {
        if (!open || lock.getReadHoldCount() == 0) {
            throw new IllegalStateException("Scheduled admission requires an active owner permit");
        }
        return verifyOwnership();
    }

    /** A thread-confined lifecycle permit; owns only the controller's read lock. */
    public final class Permit implements AutoCloseable {
        private final Thread ownerThread = Thread.currentThread();
        private boolean closed;

        private Permit() {}

        @Override
        public void close() {
            if (Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("Schedule admission permit must be closed by its owning thread");
            }
            if (!closed) {
                lock.readLock().unlock();
                closed = true;
            }
        }
    }
}
