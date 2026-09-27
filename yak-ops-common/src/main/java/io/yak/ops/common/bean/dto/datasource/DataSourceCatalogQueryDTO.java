package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据源 Catalog 表查询请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSourceCatalogQueryDTO {

    /** 数据库名称，无数据库层级时为空。 */
    @Size(max = 128, message = "数据库名称不能超过 128 个字符")
    private String database;

    /** Schema 名称，无 Schema 层级时为空。 */
    @Size(max = 128, message = "Schema 名称不能超过 128 个字符")
    private String schema;

    /** 表名搜索关键字。 */
    @Size(max = 128, message = "表名搜索关键字不能超过 128 个字符")
    private String keyword;

    /** 最大返回数量。 */
    @Min(value = 1, message = "Catalog 返回数量必须大于 0")
    @Max(value = 500, message = "Catalog 返回数量不能超过 500")
    private Integer limit = 200;
}
