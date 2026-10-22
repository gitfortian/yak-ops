package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.SecurityLevel;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned field standard without payload diagnostics. */
@Getter
@Setter
public class StandardFieldEntity extends BaseEntity {
    /** Workspace owning this standard and its category reference. */
    private String workspaceId;
    /** Immutable owning category in the same Workspace. */
    private String categoryId;
    /** Business display name of the standardized field. */
    private String cnName;
    /** Globally unique standard code shared across Workspaces. */
    private String enCode;
    /** Logical type of the standardized field. */
    private DataType dataType;
    /** Optional logical length or decimal precision and scale. */
    private String lengthPrecision;
    /** Whether consumers inherit a non-null constraint. */
    private Boolean isNotNull;
    /** Business confidentiality classification of the field. */
    private SecurityLevel securityLevel;
    /** Assigned user identity retained when membership ends. */
    private String ownerId;
    /** Optional business definition; excluded from diagnostics. */
    private String businessCaliber;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
