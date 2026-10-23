package io.yak.ops.dao.entity.modeling;

import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persists one ordered logical field of a DWD or DIM model. */
@Getter
@Setter
public class ModelFieldEntity extends BaseEntity {
    /** Workspace owning the field and its model reference. */
    private String workspaceId;
    /** Owning model; fields are replaced with the model and deleted with it. */
    private String modelId;
    /** One-based display order inside the model. */
    private int ordinal;
    /** Field code unique inside the model. */
    private String enCode;
    /** Business field display name. */
    private String cnName;
    /** Logical type shared with data standards. */
    private DataType dataType;
    /** Optional logical length or decimal precision and scale. */
    private String lengthPrecision;
    /**
     * Whether the user marked the field as part of the primary key.
     *
     * <p>The name keeps the {@code is_} prefix of the column so the camel-case mapping of {@code
     * is_primary_key} lands on this property; a {@code primaryKey} field would silently stay false.
     */
    private Boolean isPrimaryKey;
    /** Whether the layer requires a non-null value; same naming rule as {@link #isPrimaryKey}. */
    private Boolean isNotNull;
    /** Whether the field is the model partition column; same naming rule as {@link #isPrimaryKey}. */
    private Boolean isPartition;
    /** Bound field standard; null when the field is maintained by hand. */
    private String standardId;
    /** Optional business description. */
    private String description;
    /** Joined standard code for display; null on write paths or when unbound. */
    private String standardEnCode;
}
