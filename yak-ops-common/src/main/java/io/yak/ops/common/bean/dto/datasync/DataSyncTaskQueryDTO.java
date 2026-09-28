package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据同步任务分页筛选请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSyncTaskQueryDTO extends PageQueryDTO {

    /** 任务名称、来源表或目标表统一搜索词。 */
    @Size(max = 256, message = "搜索关键词不能超过 256 个字符")
    private String keyword;

    /** 同步类型。 */
    private DataSyncType syncType;

    /** 任务发布状态。 */
    private DataSyncTaskStatus status;

    /** 来源数据源 ID。 */
    private String sourceDataSourceId;

    /** 目标数据源 ID。 */
    private String targetDataSourceId;
}
