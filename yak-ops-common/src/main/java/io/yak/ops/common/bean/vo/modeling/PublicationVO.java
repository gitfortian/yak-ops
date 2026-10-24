package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.PublicationStatus;
import java.time.LocalDateTime;

/**
 * One publication record.
 *
 * <p>{@code status} is null when the stored row carries a value this build does not know; such a row
 * is still readable and is never reconciled or rewritten by guessing.
 *
 * @param id publication identity
 * @param modelId published model, null after the model was deleted
 * @param datasourceId datasource used to reach the target
 * @param datasourceRevision datasource revision pinned for the execution
 * @param database explicit target database
 * @param schema explicit target schema, null where the engine has none
 * @param table physical table name
 * @param status current status, null for an unknown stored value
 * @param ddl attempted DDL script
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
        String database,
        String schema,
        String table,
        PublicationStatus status,
        String ddl,
        String error,
        LocalDateTime createdTime,
        LocalDateTime finishedTime,
        String createBy) {}
