package io.yak.ops.common.bean.dto.datasource;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Defines pagination and filters for datasource management queries.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSourceQueryDTO extends PageQueryDTO {

    /** Name filter retained for compatibility with earlier callers. */
    @Size(max = 128, message = "数据源名称筛选不能超过 128 个字符")
    private String name;

    /** Search term covering name, connection address, database type and environment. */
    @Size(max = 256, message = "搜索关键词不能超过 256 个字符")
    private String keyword;

    /** Filter by database provider type. */
    private String dbType;

    /** Filter by datasource environment. */
    private String environment;

    /** Filter by connectivity status, such as CONNECTED, DISCONNECTED or UNKNOWN. */
    private String connStatus;
}
