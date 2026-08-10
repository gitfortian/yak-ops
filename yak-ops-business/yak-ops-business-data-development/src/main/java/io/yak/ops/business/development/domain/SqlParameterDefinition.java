package io.yak.ops.business.development.domain;

/** Named SQL parameter metadata kept in draft and immutable version snapshots. */
public record SqlParameterDefinition(
    String name,
    String type,
    boolean required,
    Object defaultValue) {
}
