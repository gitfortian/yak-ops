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
    UNSUPPORTED_PUBLICATION(19602, "当前模型或数据源暂不支持物理发布");

    private final Integer code;
    private final String message;
}
