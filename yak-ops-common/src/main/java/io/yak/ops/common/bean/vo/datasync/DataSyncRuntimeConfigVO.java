package io.yak.ops.common.bean.vo.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import lombok.Data;

/**
 * Exposes YakFlow runtime settings for synchronization tasks.
 */
@Data
public class DataSyncRuntimeConfigVO {

    /** Runtime planning policy; an absent value resolves to FIXED. */
    private DataSyncRuntimePolicy policy;

    /** JDBC cursor fetch size. */
    private Integer fetchSize;

    /** Source read batch size. */
    private Integer readBatchSize;

    /** Sink write batch size. */
    private Integer writeBatchSize;

    /** Target row count for dynamic splitting; null disables automatic range splitting. */
    private Long splitSize;

    /** Parallelism of the bounded Source Reader. */
    private Integer sourceParallelism;

    /** Connection and statement timeout, in seconds. */
    private Integer timeoutSeconds;
}
