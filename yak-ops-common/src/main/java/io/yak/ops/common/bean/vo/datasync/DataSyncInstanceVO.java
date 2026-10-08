package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步 Execution 根实例响应。
 *
 * <p>分页列表不返回任务定义快照；实例详情可以返回启动时固化的脱敏 definition snapshot。</p>
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncInstanceVO {

    /** 单次外部运行请求形成的 Execution ID；Retry Attempt 不创建新的根 ID。 */
    private String id;

    /** 产生该实例的任务 ID。 */
    private String taskId;

    /** 实例创建时固化的任务名称，后续任务改名不会回写历史实例。 */
    private String taskName;

    /** 实例启动时采用的任务定义版本。 */
    private Integer taskVersion;

    /** 实例同步类型，例如 OFFLINE、REALTIME。 */
    private String syncType;

    /** Execution 根触发方式，当前正常来源为 MANUAL / SCHEDULE；Retry Attempt 不覆盖该字段。 */
    private String triggerType;

    /** Execution 允许的最大 Attempt 总数。 */
    private Integer maxAttempts;

    /** Attempt 失败后的固定等待秒数。 */
    private Integer backoffSeconds;

    /** 当前或最终 Attempt 序号。 */
    private Integer currentAttempt;

    /** RETRY_WAITING 状态下下一次 Attempt 的计划时间。 */
    private LocalDateTime nextRetryTime;

    /** 实例生命周期状态，例如 PENDING、RUNNING、RETRY_WAITING、SUCCEEDED、FAILED、CANCELED、LOST。 */
    private String status;

    /** Source 已成功进入 Runtime Channel 的累计读取行数。 */
    private Long readRows;

    /** SinkWriter.write 已成功接收的累计写入行数。 */
    private Long writeRows;

    /** 实例实际开始执行的时间；仍处于 PENDING 时为空。 */
    private LocalDateTime startTime;

    /** 实例进入终态的时间；运行中实例为空。 */
    private LocalDateTime finishTime;

    /** 失败或 LOST 时的结构化错误码；正常实例为空。 */
    private Integer errorCode;

    /** 失败或 LOST 时已脱敏的错误信息；正常实例为空。 */
    private String errorMessage;

    /** 详情查询时返回的 Execution 脱敏定义快照；未来所有 Retry Attempt 必须复用，分页列表中为空。 */
    private DataSyncDefinitionSnapshotVO definitionSnapshot;

    /** 实例记录创建时间，通常早于或等于实际开始时间。 */
    private LocalDateTime createTime;

    /** 实例状态、指标或错误信息最近一次持久化更新时间。 */
    private LocalDateTime updateTime;
}
