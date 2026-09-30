package io.yak.ops.common.enums.datasync;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 运维中心 Data Sync 指标查询时间范围。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncOperationsRange {
    TODAY(1, true),
    LAST_7_DAYS(7, false),
    LAST_30_DAYS(30, false);

    private final int days;

    private final boolean hourly;
}
