package io.yak.ops.common.enums.modeling;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized failures owned by the Workspace data warehouse layers. */
@Getter
@RequiredArgsConstructor
public enum LayerErrorCode implements ErrorCode {
    INVALID_CODE(19201, "英文缩写须以大写字母开头，仅含大写字母、数字或下划线，最多32位"),
    DUPLICATE_CODE(19202, "英文缩写已存在"),
    BUILTIN_IMMUTABLE(19203, "默认分层只能修改绑定的物理数据源和启用状态"),
    BUILTIN_UNDELETABLE(19204, "默认分层不允许删除"),
    REVISION_CONFLICT(19205, "数据已变更，请刷新后重试"),
    INVALID_DATASOURCE(19206, "请选择当前工作空间已存在的物理数据源"),
    DATASOURCE_REQUIRED(19207, "启用分层前必须先绑定物理数据源");

    private final Integer code;
    private final String message;
}
