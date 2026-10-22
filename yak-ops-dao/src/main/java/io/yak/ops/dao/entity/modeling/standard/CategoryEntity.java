package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned directory without payload diagnostics. */
@Getter
@Setter
public class CategoryEntity extends BaseEntity {
    /** Workspace owning this directory and all parent references. */
    private String workspaceId;
    /** Immutable parent directory identity; null identifies a root. */
    private String parentId;
    /** Business display name of the directory. */
    private String name;
    /** Optimistic version advanced by successful renames. */
    private long revision;
}
