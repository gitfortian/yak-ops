package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Carries connection-test parameters for unsaved or edited datasources.
 */
@Data
public class DataSourceConnectTestDTO {

    /** Existing datasource identifier used to retain unchanged secret values while editing. */
    private String dataSourceId;

    /** Provider type used to route the connection test. */
    @NotBlank(message = "数据源类型不能为空")
    private String dbType;

    /** Structured connection parameters shared with create and update requests. */
    @Valid
    @NotNull(message = "数据源连接参数不能为空")
    private DataSourceConnectionDTO connectionParams;
}
