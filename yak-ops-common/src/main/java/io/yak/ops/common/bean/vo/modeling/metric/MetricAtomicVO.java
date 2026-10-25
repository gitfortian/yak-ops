package io.yak.ops.common.bean.vo.modeling.metric;

import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import java.time.LocalDateTime;

/**
 * Authorized atomic metric.
 *
 * @param id metric identity
 * @param processId business process that produces the measure
 * @param processName process name for display, joined on read
 * @param enCode Workspace-unique code inside atomic metrics
 * @param cnName statistical caliber display name
 * @param dataType logical type the measure returns
 * @param aggregateType aggregate that is part of the caliber
 * @param measureUnitId bound measurement unit, null when unannotated
 * @param measureUnitEnCode bound unit abbreviation for display, null when unbound
 * @param ownerId accountable owner
 * @param description optional business description
 * @param updateTime most recent accepted update timestamp
 * @param revision optimistic revision
 */
public record MetricAtomicVO(
        String id,
        String processId,
        String processName,
        String enCode,
        String cnName,
        DataType dataType,
        AggregateType aggregateType,
        String measureUnitId,
        String measureUnitEnCode,
        String ownerId,
        String description,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "MetricAtomicVO[id=" + id + ", revision=" + revision + "]";
    }
}
