package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步任务对外展示的 YakFlow 运行参数。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncRuntimeConfigVO {

    /** JDBC 游标 Fetch Size。 */
    private Integer fetchSize;

    /** Source 读取批次大小。 */
    private Integer readBatchSize;

    /** Sink 写入批次大小。 */
    private Integer writeBatchSize;

    /** 连接与语句超时秒数。 */
    private Integer timeoutSeconds;
}
