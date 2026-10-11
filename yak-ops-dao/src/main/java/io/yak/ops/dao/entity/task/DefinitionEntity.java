package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.common.enums.task.DefinitionStatus;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Stable Task Definition identity; executable parameters live only in version records. */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_definition")
public class DefinitionEntity extends BaseEntity {
    private String workspaceId;
    private String name;
    private String taskType;
    private DefinitionStatus status;
    private Integer currentVersion;
    private String remark;
    private LocalDateTime deletedAt;
}
