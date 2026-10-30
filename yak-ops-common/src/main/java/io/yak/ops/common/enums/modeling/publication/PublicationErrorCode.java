package io.yak.ops.common.enums.modeling.publication;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by physical publication and metadata collection.
 *
 * <p>These complement {@link io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode},
 * which answers malformed requests. A failure here is not malformed input: the request is well
 * formed, but the record state or the data source cannot support the operation. Keeping them apart
 * lets a client tell "fix your input" from "this is not possible now, reload and look again".
 */
@Getter
@RequiredArgsConstructor
public enum PublicationErrorCode implements ErrorCode {
    /** Reconcile or collection asked of a record whose state forbids it. */
    INVALID_PUBLICATION_STATE(19601, "当前发布状态不支持该操作，请刷新后查看最新状态"),
    /** The model or data source is structurally unable to publish at all. */
    UNSUPPORTED_PUBLICATION(19602, "当前模型或数据源暂不支持物理发布"),
    /** The computed plan contains a DROP COLUMN and the request did not carry the loss acknowledgement. */
    DANGEROUS_OPERATION_UNCONFIRMED(19603, "本次发布包含删除列操作，确认数据丢失风险后才能执行"),
    /** The publication environment is valid but the layer has no target bound for it. */
    TARGET_NOT_BOUND(19604, "该分层未绑定此环境的发布目标，请先在数仓分层中配置");

    private final Integer code;
    private final String message;
}
