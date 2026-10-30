package io.yak.ops.dao.entity.modeling;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persists one per-environment physical target bound to a warehouse layer. */
@Getter
@Setter
@TableName("yak_ops_modeling_layer_target")
public class LayerTargetEntity extends BaseEntity {
    /** Workspace owning the binding; every read is scoped by it. */
    private String workspaceId;

    /** Layer this target belongs to; deleted with the layer. */
    private String layerId;

    /** Persisted environment name as plain text: DEVELOP or PROD. */
    private String environment;

    /** Datasource used to reach the target. */
    private String dataSourceId;

    /** Default target database resolved for publications. */
    private String physicalDatabase;

    /** Default target schema; null where the engine has none. */
    private String physicalSchema;
}
