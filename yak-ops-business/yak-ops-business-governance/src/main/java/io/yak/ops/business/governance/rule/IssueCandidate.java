package io.yak.ops.business.governance.rule;

import java.math.BigDecimal;

/**
 * One accused target before it is reconciled with the stored Issue.
 *
 * <p>Evidence is numeric: the frontend owns the wording, and no free-text exception message is
 * persisted or shown.
 *
 * @param targetId identity of the governed object
 * @param targetName display name captured at detection
 * @param observedValue what the rule measured
 * @param thresholdValue what it was compared with
 * @param sampleCount samples the observation was computed from
 * @param windowDays days the observation looked back over; 0 for an instantaneous fact
 */
public record IssueCandidate(
        String targetId,
        String targetName,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        Integer sampleCount,
        Integer windowDays) {}
