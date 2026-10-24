package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence of immutable physical publication records. */
public interface PublicationRepository {
    /**
     * Inserts one pending record.
     * @param row fully populated record, already carrying its generated identity
     */
    void add(ModelPublicationEntity row);

    /**
     * Reads one record inside the Workspace.
     * @param workspaceId authorized Workspace
     * @param id publication identity
     * @return the record, or empty when it does not exist in this Workspace
     */
    Optional<ModelPublicationEntity> find(String workspaceId, String id);

    /**
     * Lists the newest records of one model.
     * @param workspaceId authorized Workspace
     * @param modelId model identity
     * @param limit maximum rows, newest first
     * @return matching records
     */
    List<ModelPublicationEntity> listByModel(String workspaceId, String modelId, int limit);

    /**
     * Moves a pending record to a terminal state.
     * @param workspaceId authorized Workspace
     * @param id publication identity
     * @param status terminal status text
     * @param error failure detail, or null
     * @param finishedTime terminal timestamp
     * @return true when the record was pending and is now finished
     */
    boolean finish(String workspaceId, String id, String status, String error, LocalDateTime finishedTime);

    /**
     * Resolves an unknown record without re-executing its DDL.
     * @param workspaceId authorized Workspace
     * @param id publication identity
     * @param status reconciled status text
     * @param error observed mismatch detail, or null
     * @param finishedTime terminal timestamp
     * @return true when the record was unknown and is now resolved
     */
    boolean reconcile(String workspaceId, String id, String status, String error, LocalDateTime finishedTime);
}
