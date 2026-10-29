package io.yak.ops.common.bean.vo.datamap;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Tables a regular expression would exclude, resolved by the datasource engine. */
@Getter
@Setter
public class DataMapCrawlExcludePreviewVO {

    /** Number of tables matched by the expression in the namespace. */
    private long matchedCount;

    /** Matched table names, bounded by the preview limit. */
    private List<String> tables;

    /** True when more tables matched than were returned. */
    private boolean truncated;
}
