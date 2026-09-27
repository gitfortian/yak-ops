package io.yak.ops.business.datasync.exception;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Data Sync 业务错误码。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncErrorCode implements ErrorCode {
    TASK_NOT_FOUND(42001, "同步任务不存在"),
    INSTANCE_NOT_FOUND(42002, "同步实例不存在"),
    DUPLICATE_TASK_NAME(42003, "同步任务名称已存在"),
    INVALID_TASK(42004, "同步任务参数不合法"),
    CREATE_TASK_FAILED(42005, "创建同步任务失败"),
    UPDATE_TASK_FAILED(42006, "更新同步任务失败"),
    DELETE_TASK_FAILED(42007, "删除同步任务失败"),
    INVALID_QUERY(42008, "同步查询参数不合法");

    private final Integer code;
    private final String message;
}
