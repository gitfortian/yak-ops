package io.yak.ops.dao.entity.schedule;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.enums.schedule.ScheduleFireReason;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Final occurrence result, persisted atomically with Task admission or its terminal rejection. */
@Getter
@Setter
@TableName("yak_ops_schedule_fire")
public class ScheduleFireEntity extends BaseEntity {
    /** Workspace owning the occurrence and historical target. */
    private String workspaceId;
    /** Schedule identity retained after logical deletion. */
    private String scheduleId;
    /** Revision copied from the fired trigger. */
    private Long scheduleRevision;
    /** Non-secret target category retained for authorized history. */
    private ScheduleTargetType targetType;
    /** Immutable target Definition identity. */
    private String targetId;
    /** Logical UTC occurrence at millisecond precision. */
    private LocalDateTime scheduledTime;
    /** Actual UTC callback time, independent of logical identity. */
    private LocalDateTime fireTime;
    /** UTC resolution time of this final submission result. */
    private LocalDateTime resolvedTime;
    /** Stable request key in the Task SCHEDULE namespace. */
    private String requestId;
    /** Final admission outcome, not physical execution status. */
    private ScheduleFireOutcome outcome;
    /** Safe reason code; null for ADMITTED. */
    private ScheduleFireReason reason;
    /** Task Instance identity; present only for ADMITTED. */
    private String instanceId;
}
