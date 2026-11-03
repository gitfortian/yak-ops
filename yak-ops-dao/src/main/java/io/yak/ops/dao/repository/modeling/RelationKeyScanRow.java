package io.yak.ops.dao.repository.modeling;

import lombok.Getter;
import lombok.Setter;

/** One model accused by the relation key-standardization scan. */
@Getter
@Setter
public class RelationKeyScanRow {
    /** Identity of the accused model. */
    private String modelId;
    /** Display name captured for the issue. */
    private String modelName;
    /** Relation that carries the offending key. */
    private String relationId;
}
