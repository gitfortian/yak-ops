package io.yak.ops.plugin.scheduler.api;

import java.time.Instant;
import java.util.List;

/** Validates schedule time rules and calculates future UTC occurrences without registering jobs. */
public interface CronService {
    /**
     * Validates a Cron expression and a named time zone, including finite expressions.
     * @param cronExpression scheduler-native Cron expression
     * @param timeZone named time zone, not an implicit server default
     * @throws IllegalArgumentException if either input is invalid
     */
    void validate(String cronExpression, String timeZone);

    /**
     * Checks an exact UTC occurrence with the same parser used by registration and preview.
     * @param cronExpression scheduler-native Cron rule
     * @param timeZone explicit named time zone
     * @param time UTC time with second precision
     * @return whether the instant is a valid occurrence
     */
    boolean matches(String cronExpression, String timeZone, Instant time);

    /**
     * Calculates a bounded, strictly increasing sequence after the supplied instant.
     * @param cronExpression scheduler-native Cron expression
     * @param timeZone named time zone used for evaluation
     * @param after exclusive lower UTC bound
     * @param count maximum number of results, between one and five
     * @return immutable future times, empty when the valid expression has expired
     * @throws IllegalArgumentException if any input is invalid
     */
    List<Instant> nextFireTimes(String cronExpression, String timeZone, Instant after, int count);
}
