package io.yak.ops.common.bean.vo.modeling.standard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.SecurityLevel;
import java.time.LocalDateTime;

/** Authorized standard attributes and a minimal owner projection. */
public record StandardFieldVO(
        String id,
        String categoryId,
        String categoryPath,
        String cnName,
        String enCode,
        DataType dataType,
        String lengthPrecision,
        @JsonProperty("isNotNull") boolean isNotNull,
        SecurityLevel securityLevel,
        String ownerId,
        String ownerName,
        boolean ownerActive,
        String businessCaliber,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "StandardFieldVO[id=" + id + ", revision=" + revision + "]";
    }
}
