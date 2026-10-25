package io.yak.ops.common.bean.vo.modeling.metric;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authorized composite metric with the references its expression resolved to.
 *
 * @param id metric identity
 * @param enCode Workspace-unique code inside composite metrics
 * @param cnName display name
 * @param expression arithmetic expression over derived-metric codes, stored as submitted
 * @param references derived metrics resolved from the expression, in first-appearance order
 * @param ownerId accountable owner
 * @param description optional business description
 * @param updateTime most recent accepted update timestamp
 * @param revision optimistic revision
 */
public record MetricCompositeVO(
        String id,
        String enCode,
        String cnName,
        String expression,
        List<Reference> references,
        String ownerId,
        String description,
        LocalDateTime updateTime,
        long revision) {
    public MetricCompositeVO {
        references = references == null ? null : List.copyOf(references);
    }

    /**
     * One derived metric the expression cites.
     *
     * @param derivedMetricId referenced metric identity
     * @param derivedEnCode referenced code as it appears in the expression
     * @param derivedCnName referenced name for display
     */
    public record Reference(String derivedMetricId, String derivedEnCode, String derivedCnName) {}

    @Override
    public String toString() {
        return "MetricCompositeVO[id=" + id + ", revision=" + revision + "]";
    }
}
