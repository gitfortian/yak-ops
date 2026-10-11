package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Carries the input for creating or editing a datasource.
 */
@Data
public class DataSourceDTO {

    /** Datasource name visible within the Workspace. */
    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 128, message = "数据源名称不能超过 128 个字符")
    private String name;

    /** Registered datasource provider type. */
    @NotBlank(message = "数据源类型不能为空")
    private String dbType;

    /** Environment code such as DEVELOP, TEST or PRODUCTION. */
    private String environment;

    /** Optional user-maintained datasource description. */
    @Size(max = 500, message = "数据源备注不能超过 500 个字符")
    private String remark;

    /** Structured connection parameters submitted by the client. */
    @Valid
    @NotNull(message = "数据源连接参数不能为空")
    private DataSourceConnectionDTO connectionParams;
}
