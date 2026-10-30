package io.yak.ops.common.bean.vo.datamap;

import lombok.Getter;
import lombok.Setter;

/** Size of one namespace, resolved by the datasource engine before a task is saved. */
@Getter
@Setter
public class DataMapCrawlScopePreviewVO {

    /** Number of tables and views the namespace holds. */
    private long tableCount;

    /** Tables a run may enumerate; a larger namespace is refused. */
    private int limit;

    /** True when {@code tableCount} reaches {@code limit}, so a run would be refused. */
    private boolean exceeded;
}
