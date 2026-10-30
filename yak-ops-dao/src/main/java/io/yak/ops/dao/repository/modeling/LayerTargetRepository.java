package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of per-environment physical targets bound to layers. */
public interface LayerTargetRepository {
    /**
     * Reads the target bound to one layer for one environment.
     * @param workspaceId authorized Workspace
     * @param layerId layer identity
     * @param environment stored environment name
     * @return the binding, or empty when the environment is not bound
     */
    Optional<LayerTargetEntity> target(String workspaceId, String layerId, String environment);

    /**
     * Lists every bound target of one layer.
     * @param workspaceId authorized Workspace
     * @param layerId layer identity
     * @return bindings ordered by environment name
     */
    List<LayerTargetEntity> listByLayer(String workspaceId, String layerId);

    /**
     * Replaces the whole binding of one layer inside the caller transaction.
     * @param workspaceId authorized Workspace
     * @param layerId layer identity
     * @param rows fully initialized replacement rows
     */
    void replace(String workspaceId, String layerId, List<LayerTargetEntity> rows);
}
