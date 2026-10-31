package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Preview request for the number of tables one namespace would enumerate. */
@Getter
@Setter
public class DataMapCrawlScopePreviewDTO {

    /** Datasource whose metadata is counted. */
    @NotBlank
    @Size(max = 64)
    private String datasourceId;

    /** Catalog slot whose tables are counted. */
    @NotBlank
    @Size(max = 128)
    private String database;

    /** Schema slot; null or blank uses the datasource binding. */
    @Size(max = 128)
    private String schema;
}
