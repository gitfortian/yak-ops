package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricModifierVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.page.PageData;

/** Owns modifier writes; modifiers anchor to one data domain of the Workspace. */
public interface MetricModifierService {
    /**
     * Reads one page of modifiers, optionally restricted to a data domain.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param domainId data-domain filter, or null for every domain
     * @param keyword search text, or null for no filter
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return the current page
     */
    PageData<MetricModifierVO> page(
            String workspaceId,
            String actor,
            String domainId,
            String keyword,
            int pageNo,
            int pageSize);
    /**
     * Reads one modifier.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the modifier
     */
    MetricModifierVO detail(String workspaceId, String actor, String id);
    /**
     * Creates a modifier under a data domain.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input submitted attributes
     * @return identity and committed revision
     */
    MetricSavedVO create(String workspaceId, String actor, MetricModifierInputDTO input);
    /**
     * Replaces a modifier's attributes under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes with the observed revision
     * @return identity and committed revision
     */
    MetricSavedVO update(String workspaceId, String actor, String id, MetricModifierUpdateDTO input);
    /**
     * Deletes a modifier; derived metrics using it restrict deletion.
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
     * Reads the derived metrics that use one modifier.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return dependants
     */
    MetricReferencesVO references(String workspaceId, String actor, String id);
}
