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
    /** Optional search term for the definition name. */
    @Size(max = 256)
    private String keyword;

    /** Optional registered plugin type filter. */
    private String taskType;
    /** Optional publication status filter, independent of runtime state. */
    private DefinitionStatus status;
    /** Safe exact-match filters on top-level plugin parameter keys. */
    private Map<String, String> parameterFilters;
}
