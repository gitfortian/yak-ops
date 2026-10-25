package io.yak.ops.common.enums.modeling.metric;

/** What one entry of a metric reference list points at. */
public enum MetricReferenceKind {
    /** A derived metric built on the record. */
    DERIVED_METRIC,
    /** A composite metric whose expression references the record. */
    COMPOSITE_METRIC,
    /** A model field that imported the record; its item id is the owning model. */
    MODEL_FIELD
}
