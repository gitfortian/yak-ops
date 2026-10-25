package io.yak.ops.common.bean.vo.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
import java.time.LocalDateTime;

/** Authorized measurement unit. */
public record StandardMeasureUnitVO(
        String id,
        MeasureUnitCategory category,
        String enCode,
        String enName,
        String cnName,
        String description,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "StandardMeasureUnitVO[id=" + id + ", revision=" + revision + "]";
    }
}
