package io.yak.ops.business.task.governance;

/**
 * Published definition whose finished scheduled runs average above the slow threshold.
 *
 * @param definitionId definition identity
 * @param displayName definition name
 * @param averageSeconds average wall-clock seconds of the finished runs in the window
 * @param sampleCount finished scheduled instances the average was computed from
 */
public record TaskSlowCandidate(String definitionId, String displayName, long averageSeconds, int sampleCount) {}
