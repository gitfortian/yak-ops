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

    /** 表所属数据库名称；数据库没有该层级时为空。 */
    private String database;

    /** 表所属 Schema 名称；数据库没有该层级时为空。 */
    private String schema;

    /** 数据库返回的物理表或视图名称。 */
    private String name;

    /** Catalog 对象类型，例如 TABLE 或 VIEW。 */
    private String type;

    /** 数据库表或视图备注；数据库未配置时可能为空。 */
    private String remarks;
}
