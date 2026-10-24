package io.yak.ops.dao.entity.modeling;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persists one metadata collection run for a publication.
 *
 * <p>A refresh inserts a new run and drops the previous one in the same transaction, so a snapshot
 * is always either the old complete set or the new complete set.
 */
@Getter
@Setter
public class MetadataCollectionEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the run. */
    private String workspaceId;
    /** Publication this run describes. */
    private String publicationId;
    /** {@code SUCCEEDED} or {@code FAILED}; read as text so unknown values surface. */
    private String status;
    /** Number of collected tables; zero when the run failed. */
    private int tableCount;
    /** Number of collected columns across all tables. */
    private int fieldCount;
    /** Collection failure detail; null on success. */
    private String error;
    /** Creation timestamp. */
    private LocalDateTime createdTime;
    /** Terminal state timestamp; null while running. */
    private LocalDateTime finishedTime;
    /** Authenticated collector identity. */
    private String createBy;
}
