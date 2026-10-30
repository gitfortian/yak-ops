package io.yak.ops.common.enums.tag;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by the tag dictionary.
 *
 * <p>Unknown keys, values and tables reuse {@code RESOURCE_NOT_EXISTS}; field lengths are enforced
 * by Bean Validation instead of a coded error. The block starts at the first free number after the
 * data map range, which the dictionary replaces for asset tagging.
 */
@Getter
@RequiredArgsConstructor
public enum TagErrorCode implements ErrorCode {
    /** The key name already exists in this Workspace. */
    DUPLICATE_TAG_KEY(19901, "标签键名称已存在"),
    /** The value name already exists in this key. */
    DUPLICATE_TAG_VALUE(19902, "标签值名称已存在"),
    /** The key or value is still bound to at least one asset. */
    TAG_IN_USE(19903, "标签仍被资产引用，请先解除引用后再删除"),
    /** The request would leave an asset with more than twenty values. */
    TOO_MANY_TAGS_PER_ASSET(19904, "单个资产的标签值数量不能超过 20 个"),
    /** The key already holds two hundred values. */
    TOO_MANY_TAG_VALUES_PER_KEY(19905, "单个标签键的标签值数量不能超过 200 个"),
    /** The caller is not a Workspace OWNER or ADMIN. */
    TAG_WRITE_FORBIDDEN(19906, "只有工作空间管理员可以维护标签字典"),
    /** The batch exceeds one hundred tables, twenty values or one thousand bindings. */
    BATCH_TOO_LARGE(19907, "批量操作超出数量上限，请缩小范围后重试");

    private final Integer code;
    private final String message;
}
