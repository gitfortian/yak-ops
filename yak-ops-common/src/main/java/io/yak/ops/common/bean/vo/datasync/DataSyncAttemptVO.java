package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步 Execution 下单次 Attempt 历史响应。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncAttemptVO {

    /** Attempt ID。 */
    private String id;

    /** 所属 Execution 根实例 ID。 */
    private String executionId;

    /** Execution 内 Attempt 序号，从 1 开始。 */
    private Integer attemptNo;

    /** Attempt 状态：PENDING / RUNNING / SUCCEEDED / FAILED / CANCELED / LOST。 */
    private String status;

    /** 本 Attempt 累计读取行数。 */
    private Long readRows;

    /** 本 Attempt 累计写入行数。 */
    private Long writeRows;

    /** 本 Attempt 实际开始时间。 */
    private LocalDateTime startTime;

    /** 本 Attempt 进入终态的完成时间。 */
    private LocalDateTime finishTime;

    /** 本 Attempt 失败时的结构化错误码。 */
    private Integer errorCode;

    /** 本 Attempt 失败时的脱敏错误信息。 */
    private String errorMessage;

    /** Attempt 创建时间。 */
    private LocalDateTime createTime;

    /** Attempt 最近更新时间。 */
    private LocalDateTime updateTime;
}
