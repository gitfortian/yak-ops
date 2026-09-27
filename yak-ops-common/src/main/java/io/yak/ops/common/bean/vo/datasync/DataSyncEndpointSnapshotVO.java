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

    private String dataSourceId;

    private String dataSourceName;

    private String dataSourceType;

    private String database;

    private String schema;

    private String table;
}
