package io.yak.ops.dao.entity.modeling;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted many-to-one relation between two Workspace logical models. */
@Getter
@Setter
public class ModelingRelationEntity extends BaseEntity {
    /** Workspace owning both models of the relation. */
    private String workspaceId;
    /** Relation name, unique per Workspace. */
    private String name;
    /** Many side model; must not be a DIM model. */
    private String fromModelId;
    /** One side model; must be a DIM model. */
    private String toModelId;
    /** Join style: INNER or LEFT. */
    private String joinType;
    /** Optional business description; null when absent. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
