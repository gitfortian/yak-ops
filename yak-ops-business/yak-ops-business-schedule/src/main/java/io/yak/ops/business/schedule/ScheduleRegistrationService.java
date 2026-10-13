package io.yak.ops.business.schedule;

import io.yak.ops.common.bean.vo.schedule.ScheduleRegistrationVO;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import java.util.List;
import java.util.Map;

/** Applies durable schedule intent to an owner-controlled engine and reports actual observations. */
public interface ScheduleRegistrationService {
    /** Reconciles changed/missing/orphaned registrations and final-fire retention in bounded batches. */
    void reconcile();
    /**
     * Re-reads committed intent and applies it; records a safe error without undoing committed intent.
     * @param workspaceId owning Workspace
     * @param scheduleId schedule whose committed intent changed
     */
    void synchronize(String workspaceId, String scheduleId);
    /**
     * Describes a batch from one engine snapshot without per-row engine queries.
     * @param schedules authorized schedule rows
     * @return observations keyed by schedule ID
     */
    Map<String, ScheduleRegistrationVO> describe(List<ScheduleEntity> schedules);
}
