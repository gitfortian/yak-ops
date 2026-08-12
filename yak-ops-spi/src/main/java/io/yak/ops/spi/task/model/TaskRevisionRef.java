package io.yak.ops.spi.task.model;

/** Stable reference to one immutable published task revision. */
public record TaskRevisionRef(
    Long assetId,
    Long revisionId,
    int revisionNo) {
}
