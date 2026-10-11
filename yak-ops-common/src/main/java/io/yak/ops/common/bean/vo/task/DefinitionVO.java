package io.yak.ops.common.bean.vo.task;

import java.time.LocalDateTime;
import lombok.Data;

/** Workspace-scoped generic Task Definition with its current validated parameters. */
@Data
public class DefinitionVO {
    private String id;
    private String name;
    private String taskType;
    private String status;
    private Integer definitionVersion;
    private Integer parameterSchemaVersion;
    private String parameters;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String updateBy;
}
