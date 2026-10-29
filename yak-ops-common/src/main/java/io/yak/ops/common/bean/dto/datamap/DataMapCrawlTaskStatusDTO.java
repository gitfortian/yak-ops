package io.yak.ops.common.bean.dto.datamap;

import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Requests one lifecycle transition of a metadata collection task. */
@Getter
@Setter
public class DataMapCrawlTaskStatusDTO {

    /** Target status: RUNNING (online) or OFFLINE (paused). */
    @NotNull
    private DataMapCrawlTaskStatus status;
}
