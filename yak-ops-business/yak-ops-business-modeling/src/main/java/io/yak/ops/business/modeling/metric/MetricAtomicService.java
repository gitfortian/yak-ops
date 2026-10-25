package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricAtomicVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.page.PageData;

/** Owns atomic-metric writes and their caliber constraints. */
public interface MetricAtomicService {
    /**
     * Reads one page of atomic metrics, optionally restricted to a business process.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param processId business-process filter, or null for every process
     * @param keyword search text, or null for no filter
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the current page
     */
    PageData<MetricAtomicVO> page(
            String workspaceId,
            String actor,
            String processId,
            String keyword,
            int pageNo,
            int pageSize);
    /**
     * Reads one atomic metric.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the metric
     */
    MetricAtomicVO detail(String workspaceId, String actor, String id);
    /**
     * Creates an atomic metric under a business process.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input submitted attributes
     * @return identity and committed revision
     */
    MetricSavedVO create(String workspaceId, String actor, MetricAtomicInputDTO input);
    /**
     * Replaces an atomic metric's attributes under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes with the observed revision
     * @return identity and committed revision
     */
    MetricSavedVO update(String workspaceId, String actor, String id, MetricAtomicUpdateDTO input);
    /**
     * Deletes an atomic metric; derived metrics built on it restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision expected optimistic revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
    /**
     * Checks the Workspace code namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param enCode submitted code
     * @param excludeId edited identity, or null for creation
     * @return true when the code is free
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Reads the derived metrics built on one atomic metric.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return dependants
     */
    MetricReferencesVO references(String workspaceId, String actor, String id);
}
