package io.yak.ops.common.bean.vo.modeling.metric;

import java.time.LocalDateTime;

/**
 * Authorized modifier.
 *
 * @param id modifier identity
 * @param domainId data domain that anchors the modifier
 * @param domainName domain name for display, joined on read
 * @param enCode abbreviation composed into derived-metric codes
 * @param cnName Chinese name composed into derived-metric names
 * @param description optional business description
 * @param updateTime most recent accepted update timestamp
 * @param revision optimistic revision
 */
public record MetricModifierVO(
        String id,
        String domainId,
        String domainName,
        String enCode,
        String cnName,
        String description,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "MetricModifierVO[id=" + id + ", revision=" + revision + "]";
    }
}
