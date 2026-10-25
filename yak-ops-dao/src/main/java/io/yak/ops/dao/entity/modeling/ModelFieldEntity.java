package io.yak.ops.dao.entity.modeling;

import io.yak.ops.common.enums.modeling.AggregateType;
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
    /** Measure function for a DWS / ADS field; null for a plain column and outside those kinds. */
    private AggregateType aggregateType;
    /** Bound field standard; null when the field is maintained by hand. */
    private String standardId;
    /** Bound lookup table; supplies code and name only when no field standard is bound. */
    private String lookupId;
    /** Bound measurement unit annotation; never changes code, name or type. */
    private String measureUnitId;
    /** Imported derived metric; advisory, never overwrites the field's own attributes. */
    private String metricId;
    /** Optional business description. */
    private String description;
    /** Joined standard code for display; null on write paths or when unbound. */
    private String standardEnCode;
    /** Joined lookup code for display; null on write paths or when unbound. */
    private String lookupEnCode;
    /** Joined unit abbreviation for display; null on write paths or when unbound. */
    private String measureUnitEnCode;
    /** Joined metric code for display; null on write paths or when not imported. */
    private String metricEnCode;
    /** Joined metric name for display; null on write paths or when not imported. */
    private String metricCnName;
}
