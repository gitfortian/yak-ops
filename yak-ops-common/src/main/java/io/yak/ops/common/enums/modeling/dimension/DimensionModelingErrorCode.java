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
    /** A suffix that does not start with a dictionary root word followed by the model's process. */
    NAMING_RULE_VIOLATION(19513, "表名不符合当前分层启用的命名规则：须以命名词根加下划线加业务过程编码开头"),
    /**
     * A collection run failed. A failed publication is not an error code: it is a recorded terminal
     * state that the publish endpoint returns, so the client can read which column differs.
     */
    METADATA_COLLECTION_FAILED(19514, "元数据采集失败，物理表可能已创建，请稍后重新采集"),
    RELATION_DUPLICATE_NAME(19515, "关系名在当前工作空间已存在"),
    RELATION_SELF(19516, "关系的两端不能是同一个模型"),
    RELATION_TARGET_NOT_DIM(19517, "关系目标必须为维度表（DIM）"),
    RELATION_FROM_IS_DIM(19518, "维度表只能作为关系的一端，不能作为多端"),
    RELATION_KEY_FIELD_MISSING(19519, "关联键字段不存在或已被移除"),
    RELATION_KEY_FIELD_UNSTANDARDIZED(19520, "关联键字段必须绑定数据标准"),
    RELATION_DUPLICATE_KEY_SET(19521, "相同模型间已存在相同关联键的关系"),
    RELATION_KEY_EMPTY(19522, "关联键不能为空"),
    MODEL_HAS_RELATIONS(19523, "模型存在语义关系，无法删除，请先删除关系"),
    RELATION_NOT_FOUND(19524, "语义关系不存在"),
    GRAIN_TOO_LONG(19525, "粒度声明不能超过200个字符"),
    SYNONYM_INVALID(19526, "同义词不合法：每项不能为空且不超过64个字符"),
    SYNONYM_TARGET_INVALID(19527, "同义词目标不存在或类型不支持"),
    RELATION_KEY_TOO_MANY(19528, "关联键对数量不能超过8个"),
    GRAIN_NOT_APPLICABLE(19529, "维度表不需要声明粒度");

    private final Integer code;
    private final String message;
}
