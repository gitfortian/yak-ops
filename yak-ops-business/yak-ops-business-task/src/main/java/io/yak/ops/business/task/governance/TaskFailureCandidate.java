package io.yak.ops.business.task.governance;

/**
 * Published definition whose latest scheduled runs all failed.
 *
 * @param definitionId definition identity
 * @param displayName definition name
 * @param consecutiveFailures length of the leading run of FAILED terminal scheduled instances
 * @param sampleCount terminal scheduled instances considered inside the window
 */
public record TaskFailureCandidate(String definitionId, String displayName, int consecutiveFailures, int sampleCount) {}
