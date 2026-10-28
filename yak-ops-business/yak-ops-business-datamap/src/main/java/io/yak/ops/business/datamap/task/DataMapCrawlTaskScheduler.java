package io.yak.ops.business.datamap.task;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for due collection tasks.
 *
 * <p>The interval is a node-local polling cadence, not the task schedule: due occurrences are
 * persisted on the task row, so an overlapping poller on another node advances each occurrence
 * conditionally and submits it at most once.
 */
@Component
public class DataMapCrawlTaskScheduler {

    @Resource
    private DataMapCrawlTaskService taskService;

    @Scheduled(
            fixedDelayString = "${yak.data-map.crawl-task-poll-interval-ms:30000}",
            initialDelayString = "${yak.data-map.crawl-task-poll-interval-ms:30000}")
    void dispatchDue() {
        taskService.dispatchDue();
    }
}
