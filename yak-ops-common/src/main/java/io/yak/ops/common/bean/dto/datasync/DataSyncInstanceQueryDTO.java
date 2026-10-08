package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.datasync.DataSyncInstanceStatus;
import io.yak.ops.common.enums.datasync.DataSyncTriggerType;
import io.yak.ops.common.enums.datasync.DataSyncType;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据同步任务实例分页筛选请求。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSyncInstanceQueryDTO extends PageQueryDTO {

    /** 指定任务 ID。 */
    private String taskId;

    /** 任务名称关键字。 */
    @Size(max = 256, message = "搜索关键词不能超过 256 个字符")
    private String keyword;

    /** 实例同步类型。 */
    private DataSyncType syncType;

    /** 实例状态。 */
    private DataSyncInstanceStatus status;

    /** 实例触发方式。 */
    private DataSyncTriggerType triggerType;

    /** 实际开始时间下界。 */
    private LocalDateTime startTimeStart;

    /** 实际开始时间上界。 */
    private LocalDateTime startTimeEnd;
}
