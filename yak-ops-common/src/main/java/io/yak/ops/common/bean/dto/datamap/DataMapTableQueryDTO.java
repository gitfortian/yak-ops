package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Search and browse filters of the asset catalog. */
@Getter
@Setter
public class DataMapTableQueryDTO {

    /** Case-insensitive substring matched against name, comments and columns. */
    @Size(max = 128)
    private String keyword;

    /** Optional datasource filter. */
    @Size(max = 64)
    private String datasourceId;

    /** Optional catalog-slot filter. */
    @Size(max = 128)
    private String database;

    /** Optional schema-slot filter. */
    @Size(max = 128)
    private String schema;

    /** Optional data domain filter. */
    @Size(max = 64)
    private String domainId;

    /** Optional accountable owner filter. */
    @Size(max = 64)
    private String ownerId;

    /** When true, only assets without a data domain are returned. */
    private Boolean unclassified;

    /** Optional tag value filter; values of one key are OR-ed, different keys are AND-ed. */
    @Size(max = 20)
    private List<String> tagValueIds;

    /** When true, MISSING assets are included. */
    private Boolean includeMissing;

    /** One-based page number. */
    @Min(1)
    private Integer pageNo = 1;

    /** Page size, capped at one hundred. */
    @Min(1)
    @Max(100)
    private Integer pageSize = 20;
}
