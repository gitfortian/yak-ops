package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 运维中心 Data Sync Execution 状态分布项。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsStatusMetricVO {

    /** Execution 状态名称。 */
    private String status;

    /** 查询范围内该状态的 Execution 数量。 */
    private Long count;
}
