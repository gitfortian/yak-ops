package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned catalog node without payload diagnostics. */
@Getter
@Setter
public class CategoryEntity extends BaseEntity {
    /** Workspace owning this directory and all parent references. */
    private String workspaceId;
    /** Immutable parent directory identity; null identifies a root. */
    private String parentId;
    /** Whether this node is a plain directory or a typed standard set. */
    private StandardNodeType nodeType;
    /** Kind of record a standard set holds; null on a directory. */
    private StandardKind standardKind;
    /** Business display name of the directory. */
    private String name;
    /** Optimistic version advanced by successful renames and kind changes. */
    private long revision;
    /** Counted records of the declared kind; zero on write paths or for an empty set. */
    private long recordCount;
}
