package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned enumerated value range without payload diagnostics. */
@Getter
@Setter
public class StandardLookupEntity extends BaseEntity {
    /** Workspace owning this lookup table and its category. */
    private String workspaceId;
    /** Immutable directory or standard set in the same Workspace. */
    private String categoryId;
    /** Workspace-unique code inside lookup tables. */
    private String enCode;
    /** Business display name. */
    private String cnName;
    /** English display name. */
    private String enName;
    /** Optional business description. */
    private String description;
    /** Counted values; zero on write paths or when the projection omits it. */
    private int valueCount;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
