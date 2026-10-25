package io.yak.ops.common.enums.modeling.metric;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized failures owned by the metric module; this enum owns the 193xx range. */
@Getter
@RequiredArgsConstructor
public enum MetricErrorCode implements ErrorCode {
    INVALID_CODE(19301, "英文编码须以小写字母开头，仅含小写字母、数字或下划线，最多64位"),
    DUPLICATE_CODE(19302, "英文编码已存在"),
    REVISION_CONFLICT(19303, "数据已变更，请刷新后重试"),
    INVALID_OWNER(19304, "请选择当前工作空间有效成员"),
    INVALID_ATOMIC_METRIC(19305, "聚合方式与数据类型不匹配"),
    INVALID_DERIVED_METRIC(19306, "派生指标的组成部分无效，或组合结果超出编码、名称长度限制"),
    INVALID_METRIC_EXPRESSION(19307, "复合指标表达式无效，或引用了不存在的派生指标"),
    REFERENCED(19308, "该指标已被资产引用，无法删除");
    private final Integer code;
    private final String message;
}
