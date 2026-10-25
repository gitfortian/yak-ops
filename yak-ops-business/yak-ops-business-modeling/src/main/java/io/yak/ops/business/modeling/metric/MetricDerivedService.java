package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricDerivedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.page.PageData;
import java.util.List;

/** Owns derived-metric writes; the composed code and name are generated from the parts. */
public interface MetricDerivedService {
    /**
     * Reads one page of derived metrics, optionally restricted to the atomic metric's process.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param processId business-process filter over the underlying atomic metric, or null
     * @param keyword search text, or null for no filter
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the current page
     */
    PageData<MetricDerivedVO> page(
            String workspaceId,
            String actor,
            String processId,
            String keyword,
            int pageNo,
            int pageSize);
    /**
     * Reads one derived metric with its parts.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the metric
     */
    MetricDerivedVO detail(String workspaceId, String actor, String id);
    /**
     * Creates a derived metric from parts; the server composes and stores code and name.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input submitted parts
     * @return identity and committed revision
     */
    MetricSavedVO create(String workspaceId, String actor, MetricDerivedInputDTO input);
    /**
     * Replaces the parts and description under the observed revision; the composition is
     * regenerated and re-checked.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement parts with the observed revision
     * @return identity and committed revision
     */
    MetricSavedVO update(String workspaceId, String actor, String id, MetricDerivedUpdateDTO input);
    /**
     * Deletes a derived metric; composite metrics and model fields importing it restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision expected optimistic revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
    /**
     * Answers whether the submitted parts would collide inside the Workspace.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param atomicMetricId atomic part
     * @param periodId period part
     * @param modifierIds ordered modifier parts, or null
     * @param excludeId edited identity, or null for creation
     * @return true when the composed code is free
     */
    boolean available(
            String workspaceId,
            String actor,
            String atomicMetricId,
            String periodId,
            List<String> modifierIds,
            String excludeId);
    /**
     * Reads the composite metrics and model fields that depend on one derived metric.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return dependants
     */
    MetricReferencesVO references(String workspaceId, String actor, String id);
}
