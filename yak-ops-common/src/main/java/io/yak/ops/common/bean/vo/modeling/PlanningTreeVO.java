package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/**
 * Authorized planning snapshot split into the two parallel trees.
 *
 * <p>Both trees reserve abbreviations in the same global namespace, so the split is presentational:
 * categories hold the public-layer business tree, data marts hold the application-layer tree.
 *
 * @param categories category roots, each carrying its domains and business processes
 * @param datamarts data mart roots, each carrying its subject areas
 */
public record PlanningTreeVO(List<PlanningNodeVO> categories, List<PlanningNodeVO> datamarts) {
    /** Copies both trees so callers cannot mutate the snapshot. */
    public PlanningTreeVO {
        categories = List.copyOf(categories);
        datamarts = List.copyOf(datamarts);
    }
}
