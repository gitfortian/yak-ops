package io.yak.ops.common.bean.vo.modeling;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;

/**
 * One ordered model field.
 *
 * @param id field identity
 * @param ordinal one-based display order
 * @param enCode field code unique inside the model
 * @param cnName business field display name
 * @param dataType logical type shared with data standards
 * @param lengthPrecision optional logical length or decimal precision and scale
 * @param isPrimaryKey whether the user marked the field as part of the primary key
 * @param isNotNull whether the layer requires a non-null value
 * @param isPartition whether the field is the model partition column
 * @param aggregateType measure function, null for a plain column and always null outside DWS / ADS
 * @param standardId bound field standard identity, null when maintained by hand
 * @param standardEnCode bound standard code for display, null when unbound
 * @param description optional business description
 */
public record ModelFieldVO(
        String id,
        int ordinal,
        String enCode,
        String cnName,
        DataType dataType,
        String lengthPrecision,
        @JsonProperty("isPrimaryKey") boolean isPrimaryKey,
        @JsonProperty("isNotNull") boolean isNotNull,
        @JsonProperty("isPartition") boolean isPartition,
        AggregateType aggregateType,
        String standardId,
        String standardEnCode,
        String description) {}
