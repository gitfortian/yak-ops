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
    /** Workspace identifier used to enforce version isolation. */
    private String workspaceId;
    /** Stable Task Definition identifier owning this version. */
    private String definitionId;
    /** Immutable version number within the owning definition. */
    private Integer version;

    /** Frozen plugin JSON payload; may contain no connection credentials. */
    @ToString.Exclude
    private String parameters;

    /** Plugin schema version used to interpret the frozen parameters. */
    private Integer parameterSchemaVersion;
}
