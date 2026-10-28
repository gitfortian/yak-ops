package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Submission of one crawl run over exactly one datasource namespace. */
@Getter
@Setter
public class DataMapCrawlSubmitDTO {

    /** Datasource whose namespace is enumerated. */
    @NotBlank
    @Size(max = 64)
    private String datasourceId;

    /**
     * Catalog slot to crawl.
     *
     * <p>Required: a run that inherits the datasource binding would enumerate every catalog the
     * account can see and collapse distinct namespaces onto the same asset identity.
     */
    @NotBlank
    @Size(max = 128)
    private String database;

    /** Schema slot to crawl; null or blank uses the datasource binding. */
    @Size(max = 128)
    private String schema;

    /**
     * Java regex of table names to keep alive without re-crawling, e.g. {@code ^tmp_}. Matching
     * tables stay ACTIVE so the missing sweep can never retire a deliberately skipped table.
     */
    @Size(max = 256)
    private String excludePattern;

    /**
     * Crawl whose failed tables this run re-attempts. The run enumerates only those names, so its
     * additions stay zero unless a named table vanished from the datasource entirely.
     */
    @Size(max = 64)
    private String retryOf;
}
