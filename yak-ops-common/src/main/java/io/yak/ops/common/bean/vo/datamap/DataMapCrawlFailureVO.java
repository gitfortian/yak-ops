package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** One per-table failure of a crawl run. */
@Getter
@AllArgsConstructor
public class DataMapCrawlFailureVO {

    /** Table whose metadata could not be read. */
    private final String tableName;

    /** Per-table failure text. */
    private final String error;

    /** Observation timestamp. */
    private final LocalDateTime createdTime;
}
