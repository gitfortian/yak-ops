package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricCompositeVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.page.PageData;

/** Owns composite-metric writes; the expression is parsed and reference-resolved, never evaluated. */
public interface MetricCompositeService {
    /**
     * Reads one page of composite metrics with their resolved references.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param keyword search text, or null for no filter
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the current page
     */
    PageData<MetricCompositeVO> page(String workspaceId, String actor, String keyword, int pageNo, int pageSize);
    /**
     * Reads one composite metric with its resolved references.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the metric
     */
    MetricCompositeVO detail(String workspaceId, String actor, String id);
    /**
     * Creates a composite metric; the expression is parsed and its references resolved before save.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input submitted attributes
     * @return identity and committed revision
     */
    MetricSavedVO create(String workspaceId, String actor, MetricCompositeInputDTO input);
    /**
     * Replaces the expression and description under the observed revision; references are
     * re-resolved.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes with the observed revision
     * @return identity and committed revision
     */
    MetricSavedVO update(String workspaceId, String actor, String id, MetricCompositeUpdateDTO input);
    /**
     * Deletes a composite metric; nothing references composites, so deletion is unrestricted.
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
}
