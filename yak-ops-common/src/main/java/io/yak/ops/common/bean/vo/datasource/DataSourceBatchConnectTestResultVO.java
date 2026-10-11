package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * Represents the connectivity-test result of one datasource in a batch.
 */
@Data
public class DataSourceBatchConnectTestResultVO {

    /** Identifier of the tested datasource. */
    private String dataSourceId;

    /** Whether the connection test succeeded. */
    private boolean connected;
}
