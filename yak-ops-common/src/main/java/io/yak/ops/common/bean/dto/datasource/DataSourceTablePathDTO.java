package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Identifies a physical table for datasource Catalog lookup.
 */
@Data
public class DataSourceTablePathDTO {

    /** Database name; absent when the provider does not expose a database level. */
    @Size(max = 128, message = "数据库名称不能超过 128 个字符")
    private String database;

    /** Schema name; absent when the provider does not expose a schema level. */
    @Size(max = 128, message = "Schema 名称不能超过 128 个字符")
    private String schema;

    /** Physical table name. */
    @NotBlank(message = "表名称不能为空")
    @Size(max = 128, message = "表名称不能超过 128 个字符")
    private String table;
}
