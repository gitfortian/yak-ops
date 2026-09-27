package io.yak.ops.common.bean.vo.datasource;

import lombok.Data;

/**
 * 数据源 Catalog 表 / 视图元数据响应。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSourceCatalogTableVO {

    private String database;

    private String schema;

    private String name;

    private String type;

    private String remarks;
}
