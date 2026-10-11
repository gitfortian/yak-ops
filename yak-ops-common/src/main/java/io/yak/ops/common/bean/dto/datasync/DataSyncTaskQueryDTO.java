package io.yak.ops.common.bean.dto.datasync;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Carries paging and filter inputs for synchronization definitions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataSyncTaskQueryDTO extends PageQueryDTO {

    /** Search term matching task name, source table or target table. */
    @Size(max = 256, message = "搜索关键词不能超过 256 个字符")
    private String keyword;

    /** Synchronization type filter. */
    private DataSyncType syncType;

    /** Task publication status filter. */
    private DataSyncTaskStatus status;

    /** Source datasource identifier filter. */
    private String sourceDataSourceId;

    /** Target datasource identifier filter. */
    private String targetDataSourceId;
}
