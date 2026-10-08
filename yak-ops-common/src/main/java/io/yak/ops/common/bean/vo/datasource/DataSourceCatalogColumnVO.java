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

    /** 数据库返回的物理字段名称，字段映射时保留原始大小写。 */
    private String name;

    /** 数据库原生字段类型名称，例如 VARCHAR、NUMBER。 */
    private String typeName;

    /** JDBC 标准类型编码，对应 java.sql.Types，用于跨数据库类型兼容判断。 */
    private Integer jdbcType;

    /** JDBC 元数据返回的字段长度或数值精度；驱动未提供时可能为空。 */
    private Integer size;

    /** 数值字段的小数位数；非数值字段或驱动未提供时可能为空。 */
    private Integer scale;

    /** 字段是否允许 NULL。 */
    private Boolean nullable;

    /** JDBC Catalog 中的字段物理顺序，用于稳定恢复表字段顺序。 */
    private Integer ordinalPosition;

    /** 字段是否属于主键。 */
    private Boolean primaryKey;

    /** 复合主键顺序，对应 JDBC KEY_SEQ；非主键或驱动未提供时为空。 */
    private Integer primaryKeyPosition;

    /** 数据库字段备注或 Comment；数据库未配置时可能为空。 */
    private String remarks;
}
