package io.yak.ops.dao.entity.modeling;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persists one Workspace data warehouse layer and its physical datasource binding. */
@Getter
@Setter
@TableName("yak_ops_modeling_layer")
public class LayerEntity extends BaseEntity {
    /** Workspace owning the layer; every read is scoped by it. */
    private String workspaceId;

    /** Uppercase abbreviation, unique inside the Workspace. */
    private String enCode;

    /** Business display name. */
    private String cnName;

    /** Bound physical datasource, required before enabling. */
    private String dataSourceId;

    /** Optional business description. */
    private String description;

    /** Whether dimension modeling may select this layer. */
    private boolean enabled;

    /** Whether this row is one of the five undeletable presets. */
    private boolean builtin;

    /** Display order; presets use 10..50 and custom layers append at 1000. */
    private int sortNo;

    /** Optimistic version advanced by every accepted update. */
    private long revision;
}
