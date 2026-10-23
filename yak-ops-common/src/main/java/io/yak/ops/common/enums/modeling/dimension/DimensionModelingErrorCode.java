package io.yak.ops.common.enums.modeling.dimension;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by concept dimensions.
 *
 * <p>{@link #HAS_DEPENDENCIES} is reserved for the dimension-table reference check: dimensions are
 * deletable today because nothing references them yet, and the check activates with logical models.
 */
@Getter
@RequiredArgsConstructor
public enum DimensionModelingErrorCode implements ErrorCode {
    INVALID_CODE(19501, "英文缩写须以小写字母开头，仅含小写字母、数字或下划线"),
    DUPLICATE_CODE(19502, "英文缩写已存在于该归属下"),
    HAS_DEPENDENCIES(19503, "存在关联对象，无法删除，请先清空"),
    REVISION_CONFLICT(19504, "数据已变更，请刷新后重试"),
    INVALID_SCOPE(19505, "维度归属必须且只能选择数据域或数据集市之一");

    private final Integer code;
    private final String message;
}
