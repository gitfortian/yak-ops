package io.yak.ops.business.datamap.crawl;

import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fails runs interrupted by a previous process before the first submission is admitted.
 *
 * <p>Recovery does not happen at shutdown, because a stopping process may not get to write
 * anything. Submissions wait for this runner through the service's recovery latch.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataMapRecoveryRunner implements ApplicationRunner {

    @Resource
    private DataMapCrawlRecovery recovery;

    @Override
    public void run(ApplicationArguments arguments) {
        recovery.recoverInterruptedRuns();
    }
}
