package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one crawl-detected change of a catalog asset. */
@Getter
@Setter
public class DataMapTableChangeEntity {
    /** Application-generated change identity. */
    private String id;
    /** Workspace owning the asset. */
    private String workspaceId;
    /** Asset the change was detected on. */
    private String tableId;
    /** Closed change-type name. */
    private String changeType;
    /** Human-readable description of what changed. */
    private String detail;
    /** Detection timestamp. */
    private LocalDateTime createdTime;
}
