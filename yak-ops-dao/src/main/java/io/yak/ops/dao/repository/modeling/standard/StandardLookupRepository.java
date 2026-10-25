package io.yak.ops.dao.repository.modeling.standard;

import io.yak.ops.dao.entity.modeling.standard.StandardLookupEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardLookupValueEntity;
import java.util.List;
import java.util.Optional;

/** Persistence of lookup tables and their ordered values inside the caller's transaction. */
public interface StandardLookupRepository {
    /**
     * Reads one page of lookup tables with their value counts.
     * @param workspaceId authorized current Workspace
     * @param query validated filters and pagination
     * @return the current page
     */
    List<StandardLookupEntity> page(String workspaceId, StandardQuery query);
    /**
     * Counts the same filter the page applies.
     * @param workspaceId authorized current Workspace
     * @param query validated filters
     * @return matching total
     */
    long count(String workspaceId, StandardQuery query);
    /**
     * Finds a lookup table without revealing other Workspaces.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @return the lookup table, or empty when absent in this Workspace
     */
    Optional<StandardLookupEntity> find(String workspaceId, String id);
    /**
     * Reads the ordered values of one lookup table.
     * @param lookupId parent identity already authorized
     * @return values in submitted order
     */
    List<StandardLookupValueEntity> values(String lookupId);
    /**
     * Inserts a lookup table; Workspace code conflicts propagate as DuplicateKeyException.
     * @param node owned entity with audit fields and expected revision
     */
    void insert(StandardLookupEntity node);
    /**
     * Replaces attributes and advances the revision atomically; the category stays fixed.
     * @param node owned entity with audit fields and expected revision
     * @return true only when the expected revision matched
     */
    boolean update(StandardLookupEntity node);
    /**
     * Replaces the whole value list under the parent's revision guard.
     * @param lookupId parent identity already authorized
     * @param rows complete replacement list in submitted order
     */
    void replaceValues(String lookupId, List<StandardLookupValueEntity> rows);
    /**
     * Deletes a lookup table at its expected revision; its values cascade.
     * @param workspaceId authorized current Workspace
     * @param id target identity
     * @param revision expected optimistic revision
     * @return true only when a matching table was deleted
     */
    boolean delete(String workspaceId, String id, long revision);
    /**
     * Checks the Workspace lookup-code namespace without reserving it.
     * @param workspaceId authorized current Workspace
     * @param enCode exact validated code
     * @param excludeId edited identity, or null for creation
     * @return true when another lookup table owns the code
     */
    boolean occupied(String workspaceId, String enCode, String excludeId);
}
