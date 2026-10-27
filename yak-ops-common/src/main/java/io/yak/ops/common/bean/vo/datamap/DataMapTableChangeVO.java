package io.yak.ops.common.bean.vo.datamap;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** One crawl-detected change of an asset. */
@Getter
@AllArgsConstructor
public class DataMapTableChangeVO {

    /** Closed change-type name. */
    private final String changeType;

    /** Human-readable description of what changed. */
    private final String detail;

    /** Detection timestamp. */
    private final LocalDateTime createdTime;
}
