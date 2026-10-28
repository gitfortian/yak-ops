package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Preview request for the tables a regular expression would exclude. */
@Getter
@Setter
public class DataMapCrawlExcludePreviewDTO {

    /** Datasource whose metadata is matched. */
    @NotBlank
    @Size(max = 64)
    private String datasourceId;

    /** Catalog slot whose tables are matched. */
    @NotBlank
    @Size(max = 128)
    private String database;

    /** Schema slot; null or blank uses the datasource binding. */
    @Size(max = 128)
    private String schema;

    /** Java regex evaluated by the datasource engine. */
    @NotBlank
    @Size(max = 256)
    private String regex;
}
