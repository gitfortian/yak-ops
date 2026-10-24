package io.yak.ops.business.modeling.publication;

import io.yak.ops.common.bean.dto.modeling.PublicationTargetDTO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.PublicationPreviewVO;
import io.yak.ops.common.bean.vo.modeling.PublicationVO;
import java.util.List;

/**
 * Physical publishing of a logical model and the metadata snapshot of the published table.
 *
 * <p>Publishing is create-or-verify: an absent table is created, and a present table is verified
 * against the model contract without being modified. The target address is always explicit — never
 * inferred from the datasource default, the table name or the layer code.
 *
 * <p>Named after the record it writes rather than after the action: {@code
 * io.yak.ops.business.task.definition.PublicationService} already exists for task artifacts, and two
 * component-scanned {@code PublicationServiceImpl} classes in one application context collide on the
 * default bean name.
 */
public interface ModelPublicationService {
    /**
     * Generates the DDL for one explicit target without recording a publication.
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param modelId model to publish
     * @param target explicit database and optional schema
     * @return resolved address, table name and generated script
     */
    PublicationPreviewVO preview(String workspaceId, String actor, String modelId, PublicationTargetDTO target);

    /**
     * Executes one publication and records it.
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param modelId model to publish
     * @param target explicit database and optional schema
     * @return the recorded publication in its terminal state
     */
    PublicationVO publish(String workspaceId, String actor, String modelId, PublicationTargetDTO target);

    /**
     * Lists publication history of one model, newest first.
     * @param workspaceId authorized Workspace
     * @param modelId model identity
     * @return recorded publications
     */
    List<PublicationVO> history(String workspaceId, String modelId);

    /**
     * Reads one publication record.
     * @param workspaceId authorized Workspace
     * @param publicationId publication identity
     * @return the record
     */
    PublicationVO detail(String workspaceId, String publicationId);

    /**
     * Resolves an {@code UNKNOWN} publication by re-reading the target, without re-executing its DDL.
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param publicationId publication identity
     * @return the reconciled record
     */
    PublicationVO reconcile(String workspaceId, String actor, String publicationId);

    /**
     * Reads the persisted metadata snapshot of one publication.
     * @param workspaceId authorized Workspace
     * @param publicationId publication identity
     * @return the newest snapshot
     */
    MetadataSnapshotVO metadata(String workspaceId, String publicationId);

    /**
     * Recollects the published table and replaces its snapshot.
     * @param workspaceId authorized Workspace
     * @param actor authenticated user
     * @param publicationId publication identity
     * @return the replaced snapshot
     */
    MetadataSnapshotVO refreshMetadata(String workspaceId, String actor, String publicationId);
}
