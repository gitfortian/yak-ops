package io.yak.ops.business.datasync.scheduler;

import io.yak.ops.common.util.StringUtils;
import java.time.Instant;
import java.util.Objects;

/**
 * 表示调度引擎已经到达某个 Data Sync 调度时间点的框架无关事件。
 *
 * @param scheduleId 触发来源的调度定义 ID
 * @param workspaceId 调度所属 Workspace ID
 * @param taskId 被触发的数据同步 Task ID
 * @param scheduledFireTime 本次 Cron 计划触发时间
 * @author weifuwan
 * @since 2026-09-29
 */
public record DataSyncScheduleFire(String scheduleId, String workspaceId, String taskId, Instant scheduledFireTime) {

    public DataSyncScheduleFire {
        requireText(scheduleId, "scheduleId");
        requireText(workspaceId, "workspaceId");
        requireText(taskId, "taskId");
        Objects.requireNonNull(scheduledFireTime, "scheduledFireTime");
    }

    private static void requireText(String value, String name) {
        if (StringUtils.isBlank(value)) throw new IllegalArgumentException(name + " must not be blank");
    }
}
