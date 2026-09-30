package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步 Execution 产品事件响应。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Data
public class DataSyncExecutionEventVO {

    /** 事件 ID。 */
    private String id;

    /** 所属 Execution 根实例 ID。 */
    private String executionId;

    /** 关联 Attempt ID；Execution 级事件为空。 */
    private String attemptId;

    /** 事件级别：INFO / WARN / ERROR。 */
    private String level;

    /** 生命周期事件类型。 */
    private String eventType;

    /** 面向用户展示的脱敏事件说明。 */
    private String message;

    /** 事件发生时间。 */
    private LocalDateTime createTime;
}
