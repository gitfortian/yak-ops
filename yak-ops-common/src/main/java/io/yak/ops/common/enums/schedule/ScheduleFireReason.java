package io.yak.ops.common.enums.schedule;

/** Safe structured reasons for final trigger resolution; no exception text is persisted. */
public enum ScheduleFireReason {
    RESOURCE_BUSY,
    STALE_SCHEDULE,
    TARGET_UNPUBLISHED,
    TARGET_DELETED,
    NOT_SCHEDULABLE,
    UNAUTHORIZED,
    DEFINITION_CHANGED,
    BINDING_CHANGED,
    MISSED_WINDOW,
    INVALID_OCCURRENCE,
    PREPARATION_FAILED
}
