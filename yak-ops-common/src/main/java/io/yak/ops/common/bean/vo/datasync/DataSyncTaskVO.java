package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步任务定义响应。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncTaskVO {

    /** 数据同步任务 ID。 */
    private String id;

    /** 同一 Workspace 内的任务名称。 */
    private String name;

    /** 数据同步类型；当前产品阶段为 OFFLINE。 */
    private String syncType;

    /** 来源数据源 ID。 */
    private String sourceDataSourceId;

    /** 来源数据源绑定或任务解析后的数据库名称；没有该层级时为空。 */
    private String sourceDatabase;

    /** 来源实际使用的 Schema；没有该层级时为空。 */
    private String sourceSchema;

    /** 来源物理表名称。 */
    private String sourceTable;

    /** 目标数据源 ID。 */
    private String targetDataSourceId;

    /** 目标数据源绑定或任务解析后的数据库名称；没有该层级时为空。 */
    private String targetDatabase;

    /** 目标实际使用的 Schema；没有该层级时为空。 */
    private String targetSchema;

    /** 目标物理表名称。 */
    private String targetTable;

    /** YakFlow 离线执行使用的读取、写入和超时参数。 */
    private DataSyncRuntimeConfigVO runtimeConfig;

    /** 当前任务定义版本，从 1 开始，任务定义每次成功修改后递增。 */
    private Integer definitionVersion;

    /** 用户维护的任务说明。 */
    private String remark;

    /** 任务创建时间。 */
    private LocalDateTime createTime;

    /** 任务定义最近一次更新时间。 */
    private LocalDateTime updateTime;
}
