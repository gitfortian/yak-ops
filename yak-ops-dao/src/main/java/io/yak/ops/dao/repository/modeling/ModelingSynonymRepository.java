package io.yak.ops.dao.repository.modeling;

import io.yak.ops.dao.entity.modeling.ModelingSynonymEntity;
import java.util.List;

/** Persistence of business synonyms keyed by annotated target. */
public interface ModelingSynonymRepository {
    /**
     * Reads the synonym set of one target.
     * @param workspaceId authorized Workspace
     * @param targetType target kind
     * @param targetId target identity
     * @return synonyms in insertion order
     */
    List<ModelingSynonymEntity> listByTarget(String workspaceId, String targetType, String targetId);

    /**
     * Deletes the whole synonym set of one target.
     * @param workspaceId authorized Workspace
     * @param targetType target kind
     * @param targetId target identity
     */
    void deleteByTarget(String workspaceId, String targetType, String targetId);

    /**
     * Inserts one synonym row.
     * @param row synonym to insert
     */
    void add(ModelingSynonymEntity row);

    /**
     * Reads synonyms of many targets of one kind in one query.
     * @param workspaceId authorized Workspace
     * @param targetType target kind
     * @param targetIds target identities
     * @return synonyms of the given targets
     */
    List<ModelingSynonymEntity> listByTargets(String workspaceId, String targetType, List<String> targetIds);
}
