package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** Aggregated visit count of one asset across the Workspace. */
@Getter
@Setter
public class DataMapViewTotalRow {
    /** Asset identity. */
    private String tableId;
    /** Summed visit counter. */
    private Long viewCount;
}
