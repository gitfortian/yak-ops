package io.yak.ops.dao.entity.modeling.standard;

import lombok.Getter;
import lombok.Setter;

/**
 * One ordered value of a lookup table.
 *
 * <p>A value has no revision and no audit columns: the whole list is replaced under its parent's
 * revision, so an individual row is never edited on its own.
 */
@Getter
@Setter
public class StandardLookupValueEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the parent lookup table. */
    private String workspaceId;
    /** Parent lookup table. */
    private String lookupId;
    /** One-based position assigned from the submitted order. */
    private int ordinal;
    /** Code of one enumerated value; unique inside its table. */
    private String valueCode;
    /** Chinese display name of the value. */
    private String cnName;
    /** English display name of the value. */
    private String enName;
    /** Optional business meaning of the value. */
    private String meaning;
}
