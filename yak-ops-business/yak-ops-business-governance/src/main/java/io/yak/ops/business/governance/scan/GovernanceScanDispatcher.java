package io.yak.ops.business.governance.scan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Hands a submitted run to a dedicated scan pool.
 *
 * <p>The pool is deliberately not the shared {@code @Async} executor: a scan holds a Workspace for
 * minutes, and starving unrelated asynchronous work would be a silent, hard-to-attribute outage.
 * Two workers and a queue of sixty-four match the accepted proposal.
 */
@Component
public class GovernanceScanDispatcher {

    private static final int QUEUE_CAPACITY = 64;

    private final GovernanceScanExecutor executor;

    private final ThreadPoolTaskExecutor pool;

    public GovernanceScanDispatcher(
            GovernanceScanExecutor executor, @Value("${yak.governance.scan.workers:2}") int workers) {
        this.executor = executor;
        this.pool = new ThreadPoolTaskExecutor();
        this.pool.setCorePoolSize(workers);
        this.pool.setMaxPoolSize(workers);
        this.pool.setQueueCapacity(QUEUE_CAPACITY);
        this.pool.setThreadNamePrefix("governance-scan-");
        this.pool.setWaitForTasksToCompleteOnShutdown(true);
        this.pool.setAwaitTerminationSeconds(30);
        this.pool.initialize();
    }

    /**
     * Queues one run.
     *
     * @return false when the pool is saturated; the caller must then release the run instead of
     *     leaving a {@code PENDING} row holding the active key
     */
    public boolean dispatch(String workspaceId, String scanId) {
        try {
            pool.execute(() -> executor.execute(workspaceId, scanId));
            return true;
        } catch (TaskRejectedException rejected) {
            return false;
        }
    }

    /** Remaining queue slots; used to refuse a submission before it inserts a run. */
    public int remainingCapacity() {
        return pool.getThreadPoolExecutor().getQueue().remainingCapacity();
    }
}
