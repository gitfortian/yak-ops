package io.yak.ops.dao.repository.modeling.metric;

import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import java.util.List;

/** On-demand reference lists across the metric kinds and the model fields that consume them. */
public interface MetricReferenceRepository {
    /**
     * Reads the derived metrics built on one atomic metric.
     * @param workspaceId authorized current Workspace
     * @param atomicId target atomic metric
     * @return dependant derived metrics ordered by composed code
     */
    List<MetricReferencesVO.Item> byAtomic(String workspaceId, String atomicId);
    /**
     * Reads the derived metrics whose composition uses one modifier.
     * @param workspaceId authorized current Workspace
     * @param modifierId target modifier
     * @return dependant derived metrics ordered by composed code
     */
    List<MetricReferencesVO.Item> byModifier(String workspaceId, String modifierId);
    /**
     * Reads the derived metrics whose composition uses one time period.
     * @param workspaceId authorized current Workspace
     * @param periodId target time period
     * @return dependant derived metrics ordered by composed code
     */
    List<MetricReferencesVO.Item> byPeriod(String workspaceId, String periodId);
    /**
     * Reads the composite metrics and model fields that depend on one derived metric.
     * @param workspaceId authorized current Workspace
     * @param derivedId target derived metric
     * @return dependants, composite metrics first, then model fields ordered by table and field
     */
    List<MetricReferencesVO.Item> byDerived(String workspaceId, String derivedId);
}
