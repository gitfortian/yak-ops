package io.yak.ops.common.bean.dto.datasync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Offline Task Cron 调度配置请求。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Data
public class DataSyncScheduleDTO {

    /** Quartz Cron 表达式。 */
    @NotBlank(message = "Cron 表达式不能为空")
    @Size(max = 128, message = "Cron 表达式不能超过 128 个字符")
    private String cronExpression;

    /** Cron 解释使用的 IANA 时区 ID，例如 Asia/Shanghai。 */
    @NotBlank(message = "时区不能为空")
    @Size(max = 64, message = "时区不能超过 64 个字符")
    private String timeZone = "Asia/Shanghai";
}
