package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 离线同步来源表与目标表字段自动映射预览请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncMappingPreviewDTO {

    @NotBlank(message = "来源数据源不能为空")
    private String sourceDataSourceId;

    @Size(max = 128, message = "来源数据库名称不能超过 128 个字符")
    private String sourceDatabase;

    @Size(max = 128, message = "来源 Schema 名称不能超过 128 个字符")
    private String sourceSchema;

    @NotBlank(message = "来源表不能为空")
    @Size(max = 128, message = "来源表名称不能超过 128 个字符")
    private String sourceTable;

    @NotBlank(message = "目标数据源不能为空")
    private String targetDataSourceId;

    @Size(max = 128, message = "目标数据库名称不能超过 128 个字符")
    private String targetDatabase;

    @Size(max = 128, message = "目标 Schema 名称不能超过 128 个字符")
    private String targetSchema;

    @NotBlank(message = "目标表不能为空")
    @Size(max = 128, message = "目标表名称不能超过 128 个字符")
    private String targetTable;
}
