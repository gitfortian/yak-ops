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
    /** Workspace-unique code inside standard fields. */
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
    /** Descriptive business preset; never generated as a physical DEFAULT clause. */
    private String defaultValue;
    /** Optional parent standard recording specialization, never inherited attributes. */
    private String parentStandardId;
    /** Optional bound lookup table. */
    private String lookupId;
    /** Optional bound measurement unit. */
    private String measureUnitId;
    /** Joined parent code for display; null on write paths or when absent. */
    private String parentStandardEnCode;
    /** Joined lookup code for display; null on write paths or when unbound. */
    private String lookupEnCode;
    /** Joined lookup Chinese name for display; null on write paths or when unbound. */
    private String lookupCnName;
    /** Joined unit abbreviation for display; null on write paths or when unbound. */
    private String measureUnitEnCode;
    /** Joined unit Chinese name for display; null on write paths or when unbound. */
    private String measureUnitCnName;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
