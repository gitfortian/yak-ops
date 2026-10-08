package io.yak.ops.common.bean.vo.datasync;

import io.yak.ops.common.enums.datasync.DataSyncRuntimePolicy;
import lombok.Data;

/**
 * 数据同步任务对外展示的 YakFlow 运行参数。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncRuntimeConfigVO {

    /** 运行参数规划策略；历史配置缺失时由服务端按 FIXED 兼容。 */
    private DataSyncRuntimePolicy policy;

    /** JDBC 游标 Fetch Size。 */
    private Integer fetchSize;

    /** Source 读取批次大小。 */
    private Integer readBatchSize;

    /** Sink 写入批次大小。 */
    private Integer writeBatchSize;

    /** 动态分片目标行数；为空时不启用自动范围分片。 */
    private Long splitSize;

    /** bounded Source Reader 并行度。 */
    private Integer sourceParallelism;

    /** 连接与语句超时秒数。 */
    private Integer timeoutSeconds;
}
