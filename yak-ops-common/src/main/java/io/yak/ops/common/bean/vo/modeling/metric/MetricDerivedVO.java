package io.yak.ops.common.bean.vo.modeling.metric;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authorized derived metric with the parts its composed code and name were built from.
 *
 * @param id metric identity
 * @param enCode composed code, period then modifiers then atomic metric joined by {@code _}
 * @param cnName composed name, period then modifiers then atomic metric concatenated
 * @param atomicMetricId atomic metric carrying the caliber
 * @param atomicEnCode atomic code for display
 * @param atomicCnName atomic name for display
 * @param periodId time period fixing the window
 * @param periodEnCode period abbreviation for display
 * @param periodCnName period name for display
 * @param modifiers ordered parts below the period
 * @param ownerId accountable owner
 * @param description optional business description
 * @param updateTime most recent accepted update timestamp
 * @param revision optimistic revision
 */
public record MetricDerivedVO(
        String id,
        String enCode,
        String cnName,
        String atomicMetricId,
        String atomicEnCode,
        String atomicCnName,
        String periodId,
        String periodEnCode,
        String periodCnName,
        List<Part> modifiers,
        String ownerId,
        String description,
        LocalDateTime updateTime,
        long revision) {
    public MetricDerivedVO {
        modifiers = modifiers == null ? null : List.copyOf(modifiers);
    }

    /**
     * One ordered modifier of the composition.
     *
     * @param modifierId modifier identity
     * @param modifierEnCode modifier abbreviation for display
     * @param modifierCnName modifier name for display
     */
    public record Part(String modifierId, String modifierEnCode, String modifierCnName) {}

    @Override
    public String toString() {
        return "MetricDerivedVO[id=" + id + ", revision=" + revision + "]";
    }
}
