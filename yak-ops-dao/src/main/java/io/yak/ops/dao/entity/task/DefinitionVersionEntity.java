package io.yak.ops.dao.entity.task;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Immutable executable Task version identified by (definitionId, version). */
@Getter
@Setter
@ToString
@TableName("yak_ops_task_definition_version")
public class DefinitionVersionEntity extends BaseEntity {
    private String workspaceId;
    private String definitionId;
    private Integer version;
    @ToString.Exclude
    private String parameters;
    private Integer parameterSchemaVersion;
}
