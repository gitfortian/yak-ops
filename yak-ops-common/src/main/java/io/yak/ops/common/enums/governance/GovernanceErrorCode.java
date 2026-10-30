package io.yak.ops.common.enums.governance;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Localized failures owned by asset governance.
 *
 * <p>Unknown Issues and scans reuse {@code RESOURCE_NOT_EXISTS}; field lengths are enforced by Bean
 * Validation instead of a coded error.
 */
@Getter
@RequiredArgsConstructor
public enum GovernanceErrorCode implements ErrorCode {
    /** More than two hundred Issue identifiers were submitted in one command. */
    BATCH_TOO_LARGE(19801, "单次最多处理 200 条治理问题"),
    /** The requested transition is not valid for the current status. */
    INVALID_ISSUE_STATE(19802, "当前状态不允许该操作"),
    /** The chosen assignee is not an active member of the caller's Workspace. */
    INVALID_OWNER(19803, "所选负责人不是当前工作空间的有效成员"),
    /** The ignore window is missing, in the past or longer than ninety days. */
    INVALID_IGNORE_WINDOW(19804, "忽略窗口必须落在 1 到 90 天之间"),
    /** The Workspace already runs a scan, or the executor queue is full. */
    SCAN_BUSY(19805, "当前工作空间已有进行中的扫描，或扫描队列已满"),
    /** The health range is reversed or longer than ninety days. */
    INVALID_DATE_RANGE(19806, "查询区间无效，最多 90 天");

    private final Integer code;
    private final String message;
}
