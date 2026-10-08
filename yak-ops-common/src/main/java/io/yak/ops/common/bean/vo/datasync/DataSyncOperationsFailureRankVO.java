package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 运维中心 Data Sync 失败 / 丢失 Task 排名项。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsFailureRankVO {

    /** Task ID。 */
    private String taskId;

    /** 最近一次异常 Execution 保存的 Task 名称快照。 */
    private String taskName;

    /** 查询范围内 FAILED Execution 数量。 */
    private Long failedCount;

    /** 查询范围内 LOST Execution 数量。 */
    private Long lostCount;

    /** FAILED + LOST Execution 数量。 */
    private Long abnormalCount;

    /** 查询范围内最近一次 FAILED / LOST Execution 时间。 */
    private LocalDateTime latestFailureTime;
}
