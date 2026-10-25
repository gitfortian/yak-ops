package io.yak.ops.common.bean.vo.modeling.metric;

import java.time.LocalDateTime;

/**
 * Authorized time period.
 *
 * @param id period identity
 * @param enCode abbreviation composed into derived-metric codes
 * @param cnName Chinese name composed into derived-metric names
 * @param description optional business description
 * @param updateTime most recent accepted update timestamp
 * @param revision optimistic revision
 */
public record MetricTimePeriodVO(
        String id,
        String enCode,
        String cnName,
        String description,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "MetricTimePeriodVO[id=" + id + ", revision=" + revision + "]";
    }
}
