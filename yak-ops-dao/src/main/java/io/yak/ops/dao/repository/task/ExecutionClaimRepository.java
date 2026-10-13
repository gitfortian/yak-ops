package io.yak.ops.dao.repository.task;

import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import java.util.List;
import java.util.Optional;

/** Owns database-enforced exclusive execution resources. */
public interface ExecutionClaimRepository {

    /** @param claim canonical exclusive claim to persist during admission */
    void add(ExecutionClaimEntity claim);

    /**
     * Identifies the actual occupied resource after a claim insertion conflict.
     * @param workspaceId owning Workspace
     * @param type canonical resource category
     * @param key canonical resource key
     * @return locked conflicting claim or empty for a different constraint failure
     */
    Optional<ExecutionClaimEntity> findResourceForUpdate(String workspaceId, String type, String key);

    /**
     * @param workspaceId authorized Workspace
     * @param instanceId owning Instance
     * @return canonical resource claims held by the Instance
     */
    List<ExecutionClaimEntity> findByInstance(String workspaceId, String instanceId);

    /**
     * Releases claims after authoritative terminal completion and writer quiescence.
     *
     * @param workspaceId authorized Workspace
     * @param instanceId conclusively terminal claim owner
     * @return number of released claims
     */
    int releaseOwned(String workspaceId, String instanceId);
}
