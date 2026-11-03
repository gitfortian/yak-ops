package io.yak.ops.business.modeling.synonym;

import io.yak.ops.common.enums.modeling.SemanticSynonymTargetType;
import java.util.List;

/** Business synonyms of standards, model fields and metrics, replaced as a whole set per target. */
public interface SemanticSynonymService {
    /**
     * Reads the synonym set of one model field addressed by model and field code.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId owning model identity
     * @param fieldCode field en_code inside the model
     * @return terms in insertion order
     */
    List<String> listForModelField(String workspaceId, String actor, String modelId, String fieldCode);

    /**
     * Replaces the synonym set of one model field atomically.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param modelId owning model identity
     * @param fieldCode field en_code inside the model
     * @param terms new synonym set
     */
    void replaceForModelField(String workspaceId, String actor, String modelId, String fieldCode, List<String> terms);

    /**
     * Reads the synonym set of one standard field or metric.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param targetType STANDARD_FIELD or METRIC
     * @param targetId stable identity of the target
     * @return terms in insertion order
     */
    List<String> list(String workspaceId, String actor, SemanticSynonymTargetType targetType, String targetId);

    /**
     * Replaces the synonym set of one standard field or metric atomically.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param targetType STANDARD_FIELD or METRIC
     * @param targetId stable identity of the target
     * @param terms new synonym set
     */
    void replace(
            String workspaceId,
            String actor,
            SemanticSynonymTargetType targetType,
            String targetId,
            List<String> terms);
}
