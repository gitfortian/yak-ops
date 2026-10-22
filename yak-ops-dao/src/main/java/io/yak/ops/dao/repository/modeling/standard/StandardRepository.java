package io.yak.ops.dao.repository.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardFieldEntity;
import java.util.List;
import java.util.Optional;

/** Persistence operations owned by the standard business transaction and Workspace catalog lock. */
public interface StandardRepository {
    /**
     * Creates or locks the Workspace catalog and reports its initialization state.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated initializing user
     * @return true only when its default directories have already been committed
     */
    boolean lockCatalog(String workspaceId, String actor);
    /**
     * Completes initialization inside the catalog-owning transaction.
     * @param workspaceId authorized current Workspace
     */
    void markInitialized(String workspaceId);
    /**
     * Reads ordered directories in the caller's authorized transaction.
     * @param workspaceId authorized current Workspace
     * @return the current Workspace directories
     */
    List<CategoryEntity> categories(String workspaceId);
    /**
     * Finds a directory without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the directory, or empty when absent in this Workspace
     */
    Optional<CategoryEntity> category(String workspaceId, String id);
    /**
     * Inserts a directory while the caller holds the catalog lock.
     * @param node owned entity with audit fields and expected revision
     */
    void insertCategory(CategoryEntity node);
    /**
     * Renames a directory and atomically advances its expected revision.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean updateCategory(CategoryEntity node);
    /**
     * Deletes an empty directory at its expected revision; foreign keys restrict dependencies.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching directory was deleted
     */
    boolean deleteCategory(String workspaceId, String id, long revision);
    /**
     * Checks whether a directory still owns fields under the catalog lock.
     * @param workspaceId authorized current Workspace
     * @param categoryId owning directory identity
     * @return true when at least one field remains
     */
    boolean hasFields(String workspaceId, String categoryId);
    /**
     * Finds a field within the authorized Workspace.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the field, or empty when absent in this Workspace
     */
    Optional<StandardFieldEntity> field(String workspaceId, String id);
    /**
     * Inserts a field; global code conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insertField(StandardFieldEntity node);
    /**
     * Updates attributes and advances revision atomically; category remains fixed.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean updateField(StandardFieldEntity node);
    /**
     * Deletes a field at its expected revision; inbound foreign keys restrict references.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when the matching field was deleted
     */
    boolean deleteField(String workspaceId, String id, long revision);
    /**
     * Checks the global standard namespace without exposing the occupying field.
     * @param enCode exact validated standard code
     * @param excludeId authorized edited identity, or null for creation
     * @return true when another field owns the exact code
     */
    boolean occupied(String enCode, String excludeId);
    /**
     * Reads the requested page in deterministic update time and identity order.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page of Workspace fields
     */
    List<StandardFieldEntity> fields(String workspaceId, FieldQuery query);
    /**
     * Counts matching fields in the same snapshot used for page reads.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the total number of matches
     */
    long countFields(String workspaceId, FieldQuery query);
}
