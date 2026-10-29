package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hypothetical future trigger times of one Cron expression in the scheduler's time zone.
 *
 * @param nextFireTimes immutable future occurrences, earliest first
 */
public record DataMapCrawlSchedulePreviewVO(List<LocalDateTime> nextFireTimes) {
    public DataMapCrawlSchedulePreviewVO {
        nextFireTimes = List.copyOf(nextFireTimes);
    }
}
