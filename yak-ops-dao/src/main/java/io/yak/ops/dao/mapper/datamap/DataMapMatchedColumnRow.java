package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** One column name that matched a catalog keyword, with its asset. */
@Getter
@Setter
public class DataMapMatchedColumnRow {
    /** Asset identity. */
    private String tableId;
    /** Matching column name. */
    private String name;
}
