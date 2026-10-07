package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * Data Sync Task 内一条稳定 Source Table → Target Table 路由。
 *
 * @author weifuwan
 * @since 2026-10-07
 */
@Data
public class DataSyncTableRouteVO {

    /** 稳定 Route ID。 */
    private String id;

    /** 来源数据库名称，无该层级时为空。 */
    private String sourceDatabase;

    /** 来源 Schema 名称，无该层级时为空。 */
    private String sourceSchema;

    /** 来源物理表名称。 */
    private String sourceTable;

    /** 目标数据库名称，无该层级时为空。 */
    private String targetDatabase;

    /** 目标 Schema 名称，无该层级时为空。 */
    private String targetSchema;

    /** 目标物理表名称。 */
    private String targetTable;

    /** 目标表不存在时是否允许按当前 Route 的 LogicalTable 自动建表。 */
    private Boolean autoCreateTable;

    /** Route 级字段映射；为空表示继续使用大小写不敏感同名映射。 */
    private DataSyncMappingVO mapping;

    /** Task 内稳定顺序，从 0 开始。 */
    private Integer sortOrder;
}
