package io.yak.ops.common.bean.dto.task;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.task.DefinitionStatus;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Plugin-neutral page request, including optional safe top-level JSON parameter filters. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionQueryDTO extends PageQueryDTO {
    @Size(max = 256)
    private String keyword;
    private String taskType;
    private DefinitionStatus status;
    private Map<String, String> parameterFilters;
}
