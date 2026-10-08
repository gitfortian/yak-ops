package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Offline Task Cron 调度定义响应。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncScheduleVO {

    /** 调度定义 ID。 */
    private String id;

    /** 关联的离线同步任务 ID。 */
    private String taskId;

    /** Quartz Cron 表达式。 */
    private String cronExpression;

    /** Cron 解释使用的 IANA 时区 ID。 */
    private String timeZone;

    /** 当前是否启用调度。 */
    private Boolean enabled;

    /** Scheduler Runtime 计算出的下一次触发时间；未启用或无下次触发时为空。 */
    private LocalDateTime nextFireTime;

    /** 调度定义创建时间。 */
    private LocalDateTime createTime;

    /** 调度定义最近更新时间。 */
    private LocalDateTime updateTime;
}
