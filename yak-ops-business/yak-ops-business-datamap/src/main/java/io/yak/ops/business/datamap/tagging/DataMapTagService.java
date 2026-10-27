package io.yak.ops.business.datamap.tagging;

import io.yak.ops.common.bean.dto.datamap.DataMapTagInputDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapTagVO;
import java.util.List;

/** Owns Workspace tags and their assignment to catalog assets. */
public interface DataMapTagService {

    /**
     * Lists every tag of the Workspace in name order.
     *
     * @param workspaceId current Workspace
     * @return all tags
     */
    List<DataMapTagVO> list(String workspaceId);

    /**
     * Creates a tag.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param input tag name
     * @return the created tag
     */
    DataMapTagVO create(String workspaceId, String actor, DataMapTagInputDTO input);

    /**
     * Renames a tag.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param tagId tag identity
     * @param input new name
     * @return the renamed tag
     */
    DataMapTagVO rename(String workspaceId, String actor, String tagId, DataMapTagInputDTO input);

    /**
     * Deletes a tag and detaches it from every asset through the foreign key.
     *
     * @param workspaceId current Workspace
     * @param tagId tag identity
     */
    void delete(String workspaceId, String tagId);

    /**
     * Replaces the asset's tag set.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param tableId asset identity
     * @param tagIds tags that remain attached
     */
    void assign(String workspaceId, String actor, String tableId, List<String> tagIds);
}
