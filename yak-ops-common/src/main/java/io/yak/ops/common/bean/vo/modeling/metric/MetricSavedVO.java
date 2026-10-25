package io.yak.ops.common.bean.vo.modeling.metric;

/** Identity and committed optimistic revision of one saved metric record. */
public record MetricSavedVO(String id, long revision) {}
