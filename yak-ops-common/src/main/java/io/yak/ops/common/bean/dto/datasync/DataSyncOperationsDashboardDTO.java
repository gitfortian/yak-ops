package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.enums.datasync.DataSyncOperationsRange;
import io.yak.ops.common.enums.datasync.DataSyncType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 运维中心 Data Sync 指标看板查询请求。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncOperationsDashboardDTO {

    /** 查询 OFFLINE 或 REALTIME 指标。 */
    @NotNull(message = "同步类型不能为空")
    private DataSyncType syncType;

    /** 指标时间范围。 */
    @NotNull(message = "时间范围不能为空")
    private DataSyncOperationsRange range;
}
