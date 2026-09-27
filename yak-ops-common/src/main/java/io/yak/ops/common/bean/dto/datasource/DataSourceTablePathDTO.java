package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源 Catalog 表定位请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSourceTablePathDTO {

    /** 数据库名称，无数据库层级时为空。 */
    @Size(max = 128, message = "数据库名称不能超过 128 个字符")
    private String database;

    /** Schema 名称，无 Schema 层级时为空。 */
    @Size(max = 128, message = "Schema 名称不能超过 128 个字符")
    private String schema;

    /** 表名称。 */
    @NotBlank(message = "表名称不能为空")
    @Size(max = 128, message = "表名称不能超过 128 个字符")
    private String table;
}
