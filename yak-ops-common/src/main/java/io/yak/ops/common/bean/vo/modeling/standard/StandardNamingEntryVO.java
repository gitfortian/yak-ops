package io.yak.ops.common.bean.vo.modeling.standard;

import java.time.LocalDateTime;

/** Authorized naming-dictionary entry. */
public record StandardNamingEntryVO(
        String id,
        String enCode,
        String enName,
        String cnName,
        String description,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "StandardNamingEntryVO[id=" + id + ", revision=" + revision + "]";
    }
}
