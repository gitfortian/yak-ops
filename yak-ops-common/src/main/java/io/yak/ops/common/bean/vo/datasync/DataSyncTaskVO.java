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

    private String id;

    private String name;

    private String syncType;

    private String sourceDataSourceId;

    private String sourceDatabase;

    private String sourceSchema;

    private String sourceTable;

    private String targetDataSourceId;

    private String targetDatabase;

    private String targetSchema;

    private String targetTable;

    private DataSyncRuntimeConfigVO runtimeConfig;

    private Integer definitionVersion;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
