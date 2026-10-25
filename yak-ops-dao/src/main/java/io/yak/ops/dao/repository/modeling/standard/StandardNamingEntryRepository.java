package io.yak.ops.dao.repository.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of the flat naming dictionary inside the caller's transaction. */
public interface StandardNamingEntryRepository {
    /**
     * Reads one page of entries.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<StandardNamingEntryEntity> page(String workspaceId, StandardQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, StandardQuery query);
    /**
     * Finds an entry without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the entry, or empty when absent in this Workspace
     */
    Optional<StandardNamingEntryEntity> find(String workspaceId, String id);
    /**
     * Reads every entry of the Workspace for name matching.
     * @param workspaceId authorized current Workspace
     * @return entries ordered by abbreviation
     */
    List<StandardNamingEntryEntity> entries(String workspaceId);
    /**
     * Inserts an entry; Workspace abbreviation conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(StandardNamingEntryEntity node);
    /**
     * Replaces attributes and advances the revision atomically.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(StandardNamingEntryEntity node);
    /**
     * Deletes an entry at its expected revision; models named under it restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching entry was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace abbreviation namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated abbreviation
     * @param excludeId edited identity, or null for creation
     * @return true when another entry owns the abbreviation
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
