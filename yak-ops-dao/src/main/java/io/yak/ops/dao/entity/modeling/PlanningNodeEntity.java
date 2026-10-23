package io.yak.ops.dao.entity.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Shared row projection for the fixed planning tables, without payload diagnostics. */
@Getter
@Setter
public class PlanningNodeEntity extends BaseEntity {
    /** Workspace owning the row and every permitted parent reference. */
    private String workspaceId;
    /** Immutable level projected from the owning table, not accepted from editable fields. */
    private NodeKind kind;
    /** Immutable parent identity; null only for a root level. */
    private String parentId;
    /** Business display name owned by the Workspace. */
    private String name;
    /** Business English name; populated only for data marts and subjects. */
    private String enName;
    /** Globally reserved ASCII code, updated atomically with its registry entry. */
    private String code;
    /** Assigned user identity, retained when membership later ends; null for subjects. */
    private String ownerId;
    /** Optional business description, null when absent. */
    private String description;
    /** Monotonically increasing version required by update and deletion. */
    private long revision;
}
