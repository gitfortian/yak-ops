package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Requests filtered datasource Catalog table metadata.
 */
@Data
public class DataSourceCatalogQueryDTO {

    /** Database name; absent when the provider has no database level. */
    @Size(max = 128, message = "数据库名称不能超过 128 个字符")
    private String database;

    /** Schema name; absent when the provider has no schema level. */
    @Size(max = 128, message = "Schema 名称不能超过 128 个字符")
    private String schema;

    /** Keyword used to filter table names. */
    @Size(max = 128, message = "表名搜索关键字不能超过 128 个字符")
    private String keyword;

    /** Maximum number of metadata results to return. */
    @Min(value = 1, message = "Catalog 返回数量必须大于 0")
    @Max(value = 500, message = "Catalog 返回数量不能超过 500")
    private Integer limit = 200;
}
