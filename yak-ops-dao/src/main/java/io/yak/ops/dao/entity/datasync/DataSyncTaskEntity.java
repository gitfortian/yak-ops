package io.yak.ops.dao.entity.datasync;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_data_sync_task 表，承载 Workspace 内的数据同步任务定义。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_data_sync_task")
public class DataSyncTaskEntity extends BaseEntity {

    /** 任务所属 Workspace ID。 */
    private String workspaceId;

    /** 同一 Workspace 内唯一的任务名称。 */
    private String name;

    /** 数据同步类型：OFFLINE 或 REALTIME。 */
    private DataSyncType syncType;

    /** 离线同步写入方式；REALTIME 当前固定保存为 APPEND。 */
    private DataSyncWriteMode writeMode;

    /** 来源数据源 ID。 */
    private String sourceDataSourceId;

    /** 来源数据库名称。 */
    private String sourceDatabase;

    /** 来源 Schema 名称。 */
    private String sourceSchema;

    /** 来源表名称。 */
    private String sourceTable;

    /** 目标数据源 ID。 */
    private String targetDataSourceId;

    /** 目标数据库名称。 */
    private String targetDatabase;

    /** 目标 Schema 名称。 */
    private String targetSchema;

    /** 目标表名称。 */
    private String targetTable;

    /** 按 syncType 持久化的 YakFlow 运行参数 JSON，不包含数据源连接凭证。 */
    private String runtimeConfig;

    /** 当前任务定义版本，从 1 开始递增。 */
    private Integer definitionVersion;

    /** 用户维护的任务备注。 */
    private String remark;
}
