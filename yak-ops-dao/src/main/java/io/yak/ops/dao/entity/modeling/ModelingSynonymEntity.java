package io.yak.ops.dao.entity.modeling;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * One business synonym of an annotated object. Targets are standards, model fields and metrics;
 * rows are replaced as a whole set per target, and the same term may exist on different targets.
 */
@Getter
@Setter
public class ModelingSynonymEntity extends BaseEntity {
    /** Workspace owning the annotated object. */
    private String workspaceId;
    /** Kind of the annotated object: STANDARD_FIELD, MODEL_FIELD or METRIC. */
    private String targetType;
    /** Identity of the annotated object. */
    private String targetId;
    /** Business synonym term, unique per target. */
    private String term;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
