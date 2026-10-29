package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Requests the next occurrences of a Spring six-field Cron expression. */
@Getter
@Setter
public class DataMapCrawlSchedulePreviewDTO {

    /** Spring six-field Cron expression to evaluate. */
    @NotBlank
    @Size(max = 128)
    private String cronExpression;
}
