package io.yak.ops.common.enums.modeling.standard;

/** What one entry of a standard reference list points at. */
public enum StandardReferenceKind {
    /** A model field that binds the record; its item id is the owning model. */
    MODEL_FIELD,
    /** A field standard that references the record. */
    FIELD_STANDARD,
    /** A model whose name was composed from the record under an enabled layer rule. */
    MODEL
}
