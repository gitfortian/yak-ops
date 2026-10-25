package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricTimePeriodVO;
import io.yak.ops.common.page.PageData;

/** Owns time-period writes; the dictionary is flat and Workspace-scoped. */
public interface MetricTimePeriodService {
    /**
     * Reads one page of time periods.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param keyword search text, or null for no filter
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the current page
     */
    PageData<MetricTimePeriodVO> page(String workspaceId, String actor, String keyword, int pageNo, int pageSize);
    /**
     * Reads one time period.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the period
     */
    MetricTimePeriodVO detail(String workspaceId, String actor, String id);
    /**
     * Creates a time period.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input submitted attributes
     * @return identity and committed revision
     */
    MetricSavedVO create(String workspaceId, String actor, MetricTimePeriodInputDTO input);
    /**
     * Replaces a period's attributes under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes with the observed revision
     * @return identity and committed revision
     */
    MetricSavedVO update(String workspaceId, String actor, String id, MetricTimePeriodUpdateDTO input);
    /**
     * Deletes a period; derived metrics using it restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision expected optimistic revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
    /**
     * Checks the Workspace abbreviation namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param enCode submitted abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when the abbreviation is free
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Reads the derived metrics that use one period.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return dependants
     */
    MetricReferencesVO references(String workspaceId, String actor, String id);
}
