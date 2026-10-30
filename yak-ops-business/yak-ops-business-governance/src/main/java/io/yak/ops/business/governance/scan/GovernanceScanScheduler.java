package io.yak.ops.business.governance.scan;

import io.yak.ops.business.governance.service.GovernanceScanService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Requests the daily complete scan of every Workspace.
 *
 * <p>The cron is an operator-facing cadence, not a correctness mechanism: a missed day leaves a gap
 * in the trend, and the next complete scan converges. A Workspace that is busy or already scanning
 * is skipped rather than queued, so a slow night cannot pile up runs.
 */
@Component
public class GovernanceScanScheduler {

    private final GovernanceScanService scanService;

    public GovernanceScanScheduler(GovernanceScanService scanService) {
        this.scanService = scanService;
    }

    @Scheduled(cron = "${yak.governance.scan.cron:0 0 0 * * *}", zone = "UTC")
    void dailyScan() {
        scanService.submitScheduledForAllWorkspaces();
    }
}
