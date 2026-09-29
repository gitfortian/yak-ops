package io.yak.ops.business.datasync.scheduler;

import java.time.Instant;
import java.util.Optional;

/**
 * Data Sync 使用的框架无关时钟边界，只负责注册 Cron、修改 Cron、移除 Cron 和查询下一次触发时间。
 *
 * <p>Task 发布状态、并发策略、Retry、Instance 生命周期都不属于该边界。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public interface ScheduleEngine {

    void validate(DataSyncScheduleDefinition definition);

    void schedule(DataSyncScheduleDefinition definition);

    void reschedule(DataSyncScheduleDefinition definition);

    void unschedule(String scheduleId);

    Optional<Instant> queryNextFireTime(String scheduleId);
}
