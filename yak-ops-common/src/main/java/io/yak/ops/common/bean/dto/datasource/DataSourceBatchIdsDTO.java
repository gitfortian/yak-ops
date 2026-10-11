package io.yak.ops.common.bean.dto.datasource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * Carries the datasource identifiers for a batch operation.
 */
@Data
public class DataSourceBatchIdsDTO {

    /** Maximum datasource count accepted by one batch request. */
    public static final int MAX_IDS = 100;

    /** Datasource identifiers to operate on. */
    @NotEmpty(message = "数据源 ID 列表不能为空")
    @Size(max = MAX_IDS, message = "单次批量操作最多支持 100 个数据源")
    private List<@NotBlank(message = "数据源 ID 不能为空") String> ids;
}
