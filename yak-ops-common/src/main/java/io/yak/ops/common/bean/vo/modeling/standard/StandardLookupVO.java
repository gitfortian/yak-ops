package io.yak.ops.common.bean.vo.modeling.standard;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authorized lookup table.
 *
 * <p>{@code values} is populated by the single-record read and {@code null} on page rows, which
 * report {@code valueCount} instead so a page of fifty tables does not carry fifty value lists.
 */
public record StandardLookupVO(
        String id,
        String categoryId,
        String categoryPath,
        String enCode,
        String cnName,
        String enName,
        String description,
        int valueCount,
        List<StandardLookupValueVO> values,
        LocalDateTime updateTime,
        long revision) {
    @Override
    public String toString() {
        return "StandardLookupVO[id=" + id + ", revision=" + revision + "]";
    }
}
