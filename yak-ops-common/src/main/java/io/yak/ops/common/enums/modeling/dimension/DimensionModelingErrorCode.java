package io.yak.ops.common.enums.modeling.dimension;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by dimension modeling: concept dimensions and the DWD/DIM models built on
 * them.
 *
 * <p>{@link #HAS_DEPENDENCIES} covers both directions of the reference graph: a concept dimension
 * referenced by a dimension table cannot be deleted, and a data domain, data mart, business process
 * or field standard referenced by a model is likewise protected.
 */
@Getter
@RequiredArgsConstructor
public enum DimensionModelingErrorCode implements ErrorCode {
    INVALID_CODE(19501, "英文缩写须以小写字母开头，仅含小写字母、数字或下划线"),
    DUPLICATE_CODE(19502, "英文缩写已存在于该归属下"),
    HAS_DEPENDENCIES(19503, "存在关联对象，无法删除，请先清空"),
    REVISION_CONFLICT(19504, "数据已变更，请刷新后重试"),
    INVALID_SCOPE(19505, "维度归属必须且只能选择数据域或数据集市之一"),
    INVALID_FIELD(19506, "字段校验失败：请为每一行补齐英文编码、中文名与类型，并保证编码不重复"),
    INVALID_MODEL(19507, "模型校验失败：明细表必须挂业务过程，维度表必须且只能归属数据域或数据集市之一"),
    INVALID_TABLE_NAME(19508, "表名须以当前分层的英文缩写加下划线开头，总长不超过64个字符"),
    LAYER_NOT_SELECTABLE(19509, "所选分层未启用或未绑定物理数据源"),
    UNKNOWN_MODEL_TYPE(19510, "模型类型无法识别，不允许编辑"),
    PARTITION_RULE(19511, "分区字段只能有一个，且类型必须为 STRING"),
    DUPLICATE_TABLE_NAME(19512, "表名在当前工作空间已存在"),
    /**
     * A collection run failed. A failed publication is not an error code: it is a recorded terminal
     * state that the publish endpoint returns, so the client can read which column differs.
     */
    METADATA_COLLECTION_FAILED(19514, "元数据采集失败，物理表可能已创建，请稍后重新采集");

    private final Integer code;
    private final String message;
}
