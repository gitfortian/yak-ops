package io.yak.ops.business.modeling.standard;

import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardLookupUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardLookupVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.page.PageData;

/** Workspace lookup tables: enumerated value ranges shared by standards and model fields. */
public interface StandardLookupService {
    /**
     * Reads one page of lookup tables with their value counts.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param categoryId fixed category, or null for every category
     * @param keyword global search text, or null
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return ordered tables and the matching total
     */
    PageData<StandardLookupVO> page(
            String workspaceId, String actor, String categoryId, String keyword, int pageNo, int pageSize);
    /**
     * Reads one lookup table with its ordered values.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the table and its values
     */
    StandardLookupVO detail(String workspaceId, String actor, String id);
    /**
     * Creates a lookup table and its whole value list in one transaction.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param categoryId owning directory or standard set
     * @param input editable attributes and the ordered values
     * @return committed identity and revision
     */
    StandardSavedVO create(String workspaceId, String actor, String categoryId, StandardLookupInputDTO input);
    /**
     * Replaces attributes and the whole value list under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes, ordered values and expected revision
     * @return committed identity and incremented revision
     */
    StandardSavedVO update(String workspaceId, String actor, String id, StandardLookupUpdateDTO input);
    /**
     * Deletes a lookup table; inbound references restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision editor's expected revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
    /**
     * Checks Workspace code availability after authorizing any excluded record.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param enCode exact candidate code
     * @param excludeId edited identity, or null
     * @return true when no other lookup table owns the code
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Reads the field standards and model fields that depend on one lookup table.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the reference list
     */
    StandardReferencesVO references(String workspaceId, String actor, String id);
}
