package io.yak.ops.business.datasync.scheduler;

import io.yak.ops.common.util.StringUtils;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 描述调度引擎创建 Data Sync Cron Trigger 所需的最小定义，不承载 Task 配置、运行参数或数据源凭证。
 *
 * @param scheduleId Data Sync 调度定义 ID
 * @param workspaceId 调度所属 Workspace ID
 * @param taskId 被触发的数据同步 Task ID
 * @param cronExpression Quartz Cron 表达式
 * @param timeZone Cron 解释使用的显式时区
 * @author weifuwan
 * @since 2026-09-29
 */
public record DataSyncScheduleDefinition(
        String scheduleId, String workspaceId, String taskId, String cronExpression, ZoneId timeZone) {

    public DataSyncScheduleDefinition {
        requireText(scheduleId, "scheduleId");
        requireText(workspaceId, "workspaceId");
        requireText(taskId, "taskId");
        requireText(cronExpression, "cronExpression");
        Objects.requireNonNull(timeZone, "timeZone");
    }

    private static void requireText(String value, String name) {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException(name + " must not be blank");
    }
}
