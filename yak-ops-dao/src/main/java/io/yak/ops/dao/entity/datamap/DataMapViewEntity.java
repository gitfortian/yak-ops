package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one member's bounded visit counter for a catalog asset. */
@Getter
@Setter
public class DataMapViewEntity {
    /** Workspace owning the member and asset. */
    private String workspaceId;
    /** Member who visited the asset. */
    private String userId;
    /** Visited asset. */
    private String tableId;
    /** Bounded visit counter for this member and asset. */
    private Long viewCount;
    /** First recorded visit. */
    private LocalDateTime firstViewedTime;
    /** Most recent recorded visit. */
    private LocalDateTime lastViewedTime;
}
