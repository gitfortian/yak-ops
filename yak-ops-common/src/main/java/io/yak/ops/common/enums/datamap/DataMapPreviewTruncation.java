package io.yak.ops.common.enums.datamap;

/** Why a preview response stopped before exhausting its row cap. */
public enum DataMapPreviewTruncation {
    /** The fixed row cap was reached. */
    ROWS,
    /** The response byte budget was spent before the row cap. */
    SIZE
}
