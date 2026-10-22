package io.yak.ops.common.enums.modeling;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized planning failures with stable codes for field and conflict handling. */
@Getter
@RequiredArgsConstructor
public enum ModelingErrorCode implements ErrorCode {
    INVALID_CODE(19001, "英文编码格式不正确"),
    DUPLICATE_CODE(19002, "英文编码已存在"),
    HAS_DEPENDENCIES(19003, "存在关联子节点或资产，无法删除，请先清空"),
    REVISION_CONFLICT(19004, "数据已变更，请刷新后重试"),
    INVALID_OWNER(19005, "负责人不是当前工作空间有效成员");

    private final Integer code;
    private final String message;
}
