package io.yak.ops.common.bean.vo.datasync;

import lombok.Data;

/**
 * 数据同步实例中的单端资源快照，只包含非敏感 Datasource 元数据与表路径。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncEndpointSnapshotVO {

    /** 实例启动时引用的数据源 ID，用于关联产品资源。 */
    private String dataSourceId;

    /** 实例启动时的数据源展示名称快照。 */
    private String dataSourceName;

    /** 实例启动时的数据源类型，例如 MYSQL、ORACLE、POSTGRE_SQL。 */
    private String dataSourceType;

    /** 实际执行使用的数据库名称；数据源没有该层级时为空。 */
    private String database;

    /** 实际执行使用的 Schema；数据源没有该层级时为空。 */
    private String schema;

    /** 实际执行读写的物理表名称。 */
    private String table;
}
