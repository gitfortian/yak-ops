package io.yak.ops.business.datamap.task;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.scheduling.support.CronExpression;

/**
 * Evaluates the six-field Cron expressions stored on collection tasks.
 *
 * <p>The expression and the evaluation zone are the node's: occurrences are persisted as local
 * timestamps, so a task does not silently shift to another zone when its owner changes.
 */
public final class DataMapCrawlCron {

    private DataMapCrawlCron() {}

    /**
     * Parses an expression.
     *
     * @param expression Spring six-field Cron expression
     * @return the parsed expression
     * @throws IllegalArgumentException when the syntax is invalid or the expression never fires
     */
    public static CronExpression parse(String expression) {
        CronExpression cron = CronExpression.parse(expression.trim());
        if (cron.next(LocalDateTime.now().atZone(ZoneId.systemDefault())) == null) {
            throw new IllegalArgumentException("Cron expression never fires");
        }
        return cron;
    }

    /**
     * Resolves the occurrence strictly after the reference time.
     *
     * @param cron parsed expression
     * @param after reference time
     * @return next occurrence, or null when the expression is exhausted
     */
    public static LocalDateTime next(CronExpression cron, LocalDateTime after) {
        ZonedDateTime next = cron.next(after.atZone(ZoneId.systemDefault()));
        return next == null ? null : next.toLocalDateTime();
    }
}
