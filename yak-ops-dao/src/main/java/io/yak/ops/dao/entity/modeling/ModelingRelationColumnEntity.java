package io.yak.ops.dao.entity.modeling;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * One ordered join key pair of a relation. Both sides store field {@code en_code} values, never
 * field row ids: model fields are replaced wholesale on every save, so codes are the stable
 * business contract. Resolution joins the owning model's fields on {@code (model_id, en_code)};
 * a code that no longer resolves is a first-class broken state, never a cascade delete.
 */
@Getter
@Setter
public class ModelingRelationColumnEntity extends BaseEntity {
    /** Workspace owning the relation. */
    private String workspaceId;
    /** Owning relation. */
    private String relationId;
    /** Zero-based position; the from and to codes share it. */
    private Integer ordinal;
    /** en_code of the many-side model field. */
    private String fromFieldCode;
    /** en_code of the one-side model field. */
    private String toFieldCode;
    /** Optimistic version inherited from the owning relation; pairs are replaced as a set. */
    private long revision;
}
