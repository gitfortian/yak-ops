package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * 数据源 Catalog 字段元数据响应。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSourceCatalogColumnVO {

    private String name;

    private String typeName;

    private Integer jdbcType;

    private Integer size;

    private Integer scale;

    private Boolean nullable;

    private Integer ordinalPosition;

    private Boolean primaryKey;

    private String remarks;
}
