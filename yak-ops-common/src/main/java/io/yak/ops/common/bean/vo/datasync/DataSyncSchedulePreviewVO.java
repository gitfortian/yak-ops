package io.yak.ops.common.bean.vo.datasync;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * Data Sync Cron 调度未来触发时间预览。
 *
 * @author weifuwan
 * @since 2026-10-02
 */
@Data
public class DataSyncSchedulePreviewVO {

    /** 参与预览的 Quartz Cron 表达式。 */
    private String cronExpression;

    /** Cron 解释使用的 IANA 时区。 */
    private String timeZone;

    /** 按指定时区计算得到的未来触发时间。 */
    private List<LocalDateTime> nextFireTimes;
}
