package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one per-table failure reported by a crawl run. */
@Getter
@Setter
public class DataMapCrawlFailureEntity {
    /** Application-generated failure identity. */
    private String id;
    /** Workspace owning the run. */
    private String workspaceId;
    /** Run that observed the failure. */
    private String crawlId;
    /** Table whose metadata could not be read. */
    private String tableName;
    /** Per-table failure text. */
    private String error;
    /** Observation timestamp. */
    private LocalDateTime createdTime;
}
