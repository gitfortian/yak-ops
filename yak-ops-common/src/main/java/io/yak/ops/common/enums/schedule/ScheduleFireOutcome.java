package io.yak.ops.common.enums.schedule;

/** Final submission outcome for one logical occurrence, independent of Task execution status. */
public enum ScheduleFireOutcome {
    ADMITTED,
    SKIPPED,
    REJECTED,
    SUBMISSION_FAILED
}
