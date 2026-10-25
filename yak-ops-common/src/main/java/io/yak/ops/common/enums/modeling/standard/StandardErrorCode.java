package io.yak.ops.common.enums.modeling.standard;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized failures owned by data standards. */
@Getter
@RequiredArgsConstructor
public enum StandardErrorCode implements ErrorCode {
    INVALID_CODE(19101, "英文编码须以小写字母开头，仅含小写字母、数字或下划线，最多64位"),
    DUPLICATE_CODE(19102, "英文编码已存在"),
    HAS_DEPENDENCIES(19103, "存在子分类或字段标准，无法删除"),
    REVISION_CONFLICT(19104, "数据已变更，请刷新后重试"),
    INVALID_OWNER(19105, "请选择当前工作空间有效成员"),
    INVALID_PRECISION(19106, "长度或精度与数据类型不匹配"),
    DEPTH_LIMIT(19107, "分类层级不能超过32级"),
    REFERENCED(19108, "该标准已被资产引用，无法删除"),
    INVALID_PARENT_STANDARD(19109, "父标准无效：不能选择自身、跨工作空间、成环或超过8级"),
    INVALID_LOOKUP_VALUE(19110, "标准代码的取值列表缺失，或存在空的、重复的取值编码"),
    STANDARD_SET_KIND_LOCKED(19111, "标准集已存在记录，不能再修改其收录类型");
    private final Integer code;
    private final String message;
}
