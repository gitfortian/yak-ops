package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/** Immutable executable version for a future Workflow node reference. */
@Data
public class DefinitionVersionVO {
    private String definitionId;
    private Integer version;
    private Integer parameterSchemaVersion;
    private String parameters;
    private LocalDateTime createTime;
}
