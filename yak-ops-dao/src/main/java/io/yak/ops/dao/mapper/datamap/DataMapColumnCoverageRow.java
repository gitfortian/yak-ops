package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** How many columns of the active assets carry a source comment. */
@Getter
@Setter
public class DataMapColumnCoverageRow {
    /** Columns of active assets. */
    private Long columnTotal;
    /** Columns whose comment is neither null nor blank. */
    private Long describedColumns;
}
