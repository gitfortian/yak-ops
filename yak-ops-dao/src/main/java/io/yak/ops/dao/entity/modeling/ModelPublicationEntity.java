package io.yak.ops.dao.entity.modeling;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Persists one immutable physical publication attempt.
 *
 * <p>Append-only on purpose: a retry inserts a new row instead of rewriting the previous outcome, so
 * the history stays auditable. {@code modelId} becomes null when the logical model is deleted while
 * the record and its metadata snapshot are retained.
 */
@Getter
@Setter
public class ModelPublicationEntity {
    /** Application-generated identity. */
    private String id;
    /** Workspace owning the record. */
    private String workspaceId;
    /** Published model, or null after the model was deleted. */
    private String modelId;
    /** Datasource used to reach the target. */
    private String datasourceId;
    /** Datasource revision pinned for this execution. */
    private long datasourceRevision;
    /** Publication environment the layer target was resolved for; null on legacy rows. */
    private String environment;
    /** Planned operation: CREATE, ALTER or NONE; null on legacy rows. */
    private String operation;
    /** Explicit target database confirmed by the layer binding. */
    private String physicalDatabase;
    /** Explicit target schema; null where the engine has none. */
    private String physicalSchema;
    /** Physical table name, which is the model name and is never renamed. */
    private String physicalTable;
    /** Persisted status text; read as text so an unknown value surfaces instead of failing the read. */
    private String status;
    /** Schema diff snapshot serialized as JSON; empty list for a create. */
    private String changesJson;
    /** Execution duration in milliseconds, observed read included. */
    private Long durationMs;
    /** Ordered execution log: statements, per-statement durations, stack traces. */
    private String executionLog;
    /** Generated DDL script attempted by this publication. */
    private String ddl;
    /** Failure or mismatch detail; null while pending and on success. */
    private String error;
    /** Creation timestamp. */
    private LocalDateTime createdTime;
    /** Terminal state timestamp; null while pending. */
    private LocalDateTime finishedTime;
    /** Authenticated publisher identity. */
    private String createBy;
}
