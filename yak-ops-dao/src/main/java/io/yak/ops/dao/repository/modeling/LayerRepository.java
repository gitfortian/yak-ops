package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of Workspace layers, their presets and bindable datasources. */
public interface LayerRepository {
    /**
     * Reads every layer of one Workspace in preset-first display order.
     * @param workspaceId authorized Workspace
     * @return complete layer projection
     */
    List<LayerEntity> layers(String workspaceId);
    /**
     * Finds a layer without exposing another Workspace.
     * @param workspaceId authorized Workspace
     * @param id layer identity
     * @return matching row or empty
     */
    Optional<LayerEntity> layer(String workspaceId, String id);
    /**
     * Counts committed presets to skip redundant seeding.
     * @param workspaceId authorized Workspace
     * @return number of preset rows
     */
    long builtinCount(String workspaceId);
    /**
     * Serializes preset seeding for one Workspace inside the caller transaction.
     *
     * <p>Concurrent first readers otherwise insert the same five rows through an
     * upsert and deadlock on the shared unique index.
     *
     * @param workspaceId authorized Workspace to lock
     */
    void lockWorkspace(String workspaceId);
    /**
     * Inserts presets idempotently in the caller transaction.
     * @param rows fully initialized preset rows
     */
    void seed(List<LayerEntity> rows);
    /**
     * Inserts one custom layer and surfaces a duplicate abbreviation.
     * @param row fully initialized row
     */
    void add(LayerEntity row);
    /**
     * Replaces editable fields with an optimistic guard.
     * @param row replacement fields including the expected revision
     * @return true if the matching row was updated
     */
    boolean update(LayerEntity row);
    /**
     * Toggles the layer naming rule with an optimistic guard.
     * @param workspaceId authorized Workspace
     * @param id layer identity
     * @param enabled requested rule state
     * @param revision expected version
     * @param actor authenticated updater
     * @return true if the matching row was updated
     */
    boolean updateNamingRule(String workspaceId, String id, boolean enabled, long revision, String actor);
    /**
     * Removes a layer with an optimistic guard.
     * @param workspaceId authorized Workspace
     * @param id layer identity
     * @param revision expected version
     * @return true if the matching row was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace-local abbreviation without disclosing its owner.
     * @param workspaceId authorized Workspace
     * @param enCode normalized uppercase abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when another layer owns the abbreviation
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
    /**
     * Lists datasources a layer may bind inside the Workspace.
     * @param workspaceId authorized Workspace
     * @return datasources without connection parameters
     */
    List<DataSourceEntity> datasources(String workspaceId);
    /**
     * Confirms a datasource belongs to the Workspace before binding.
     * @param workspaceId authorized Workspace
     * @param id datasource identity
     * @return true when the datasource exists in the Workspace
     */
    boolean datasourceExists(String workspaceId, String id);
}
