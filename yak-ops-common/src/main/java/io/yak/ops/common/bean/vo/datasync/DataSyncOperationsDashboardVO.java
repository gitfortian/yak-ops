package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 运维中心 Data Sync 指标看板读模型。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsDashboardVO {

    /** OFFLINE / REALTIME。 */
    private String syncType;

    /** TODAY / LAST_7_DAYS / LAST_30_DAYS。 */
    private String range;

    /** 查询窗口开始时间，包含。 */
    private LocalDateTime rangeStart;

    /** 查询窗口结束时间，不包含未来数据。 */
    private LocalDateTime rangeEnd;

    /** 聚合摘要。 */
    private DataSyncOperationsSummaryVO summary;

    /** 连续零填充时间趋势。 */
    private List<DataSyncOperationsTrendPointVO> trend;

    /** 查询范围内 Execution 状态分布。 */
    private List<DataSyncOperationsStatusMetricVO> statusDistribution;

    /** 查询范围内 FAILED / LOST Task Top 5。 */
    private List<DataSyncOperationsFailureRankVO> failureRanking;
}
