package io.yak.ops.business.governance.scan;

import io.yak.ops.dao.mapper.governance.GovernanceRuleResultMapper;
import io.yak.ops.dao.mapper.governance.GovernanceScanRunMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clears the runs a previous process left live.
 *
 * <p>The product is single-node, so a {@code PENDING} or {@code RUNNING} row at startup cannot still
 * be executing. Leaving it would hold the unique active key forever and turn every later submission
 * into {@code SCAN_BUSY}. A clustered deployment must revisit this sweep rather than let two nodes
 * run it.
 */
@Component
public class GovernanceStartupRecovery {

    /** Reason code recorded on a run the restart interrupted. */
    public static final String APP_RESTART = "APP_RESTART";

    private static final Logger log = LoggerFactory.getLogger(GovernanceStartupRecovery.class);

    private final GovernanceScanRunMapper scanRunMapper;

    private final GovernanceRuleResultMapper ruleResultMapper;

    public GovernanceStartupRecovery(
            GovernanceScanRunMapper scanRunMapper, GovernanceRuleResultMapper ruleResultMapper) {
        this.scanRunMapper = scanRunMapper;
        this.ruleResultMapper = ruleResultMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        int rules = ruleResultMapper.markAllRunningFailed(APP_RESTART, now);
        int runs = scanRunMapper.failAllLive(APP_RESTART, now);
        if (runs > 0 || rules > 0) {
            log.warn(
                    "governance startup recovery failed {} interrupted run(s) and {} rule result(s) with {}",
                    runs,
                    rules,
                    APP_RESTART);
        }
    }
}
