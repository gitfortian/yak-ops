package io.yak.ops.dao.repository.schedule;

import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.schedule.ScheduleFireEntity;
import java.time.LocalDateTime;
import java.util.Optional;

/** Final fire evidence; new results are serialized by the owning Schedule row. */
public interface ScheduleFireRepository {
    /**
     * Reads committed final evidence without opening an admission transaction.
     * @param workspaceId owning Workspace
     * @param requestId stable occurrence key
     * @return final result or empty
     */
    Optional<ScheduleFireEntity> find(String workspaceId, String requestId);
    /**
     * Reads current final evidence after Definition/Schedule locks are acquired.
     * @param workspaceId owning Workspace
     * @param requestId stable occurrence key
     * @return locked final evidence or empty
     */
    Optional<ScheduleFireEntity> findForUpdate(String workspaceId, String requestId);
    /**
     * Inserts a final result in the same transaction as admission or terminal rejection.
     * @param fire initialized Workspace-scoped result
     */
    void add(ScheduleFireEntity fire);
    /**
     * Queries authorized history, including logically deleted schedules.
     * @param workspaceId authorized Workspace
     * @param scheduleId retained schedule identity
     * @param pageNo one-based page number
     * @param pageSize bounded maximum results
     * @param outcome optional final outcome
     * @param from optional inclusive UTC lower bound
     * @param until optional exclusive UTC upper bound
     * @return final fire page in stable logical-time order
     */
    PageData<ScheduleFireEntity> page(
            String workspaceId,
            String scheduleId,
            int pageNo,
            int pageSize,
            ScheduleFireOutcome outcome,
            LocalDateTime from,
            LocalDateTime until);
    /**
     * Atomically advances the Schedule retention floor and removes older final evidence.
     * @param workspaceId owning Workspace
     * @param scheduleId retained identity, including deleted schedules
     * @param before exclusive UTC retention floor
     */
    void retire(String workspaceId, String scheduleId, LocalDateTime before);
}
