package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.PublicationEnvironment;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One publication record.
 *
 * <p>{@code status} is null when the stored row carries a value this build does not know; such a
 * row is still readable and is never reconciled or rewritten by guessing. {@code environment} and
 * {@code operation} are null on rows written before those columns existed.
 *
 * @param id publication identity
 * @param modelId published model, null after the model was deleted
 * @param datasourceId datasource used to reach the target
 * @param datasourceRevision datasource revision pinned for the execution
 * @param environment publication environment the layer target was resolved for
 * @param database explicit target database
 * @param schema explicit target schema, null where the engine has none
 * @param table physical table name
 * @param operation CREATE, ALTER or NONE, null on legacy rows
 * @param status current status, null for an unknown stored value
 * @param changes schema diff snapshot taken before execution, empty for a create
 * @param ddl attempted DDL script
 * @param durationMs execution duration in milliseconds, null on legacy or pending rows
 * @param executionLog ordered execution log with per-statement timings and stack traces
 * @param error failure or mismatch detail, null while pending and on success
 * @param createdTime creation timestamp
 * @param finishedTime terminal timestamp, null while pending
 * @param createBy authenticated publisher identity
 */
public record PublicationVO(
        String id,
        String modelId,
        String datasourceId,
        long datasourceRevision,
        PublicationEnvironment environment,
        String database,
        String schema,
        String table,
        String operation,
        PublicationStatus status,
        List<SchemaChangeVO> changes,
        String ddl,
        Long durationMs,
        String executionLog,
        String error,
        LocalDateTime createdTime,
        LocalDateTime finishedTime,
        String createBy) {}
