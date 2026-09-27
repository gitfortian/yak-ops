package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 数据同步任务实例响应，不暴露内部 definition snapshot。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class DataSyncInstanceVO {

    private String id;

    private String taskId;

    private String taskName;

    private Integer taskVersion;

    private String triggerType;

    private String status;

    private Long readRows;

    private Long writeRows;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Integer errorCode;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
