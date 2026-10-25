package io.yak.ops.business.modeling.standard;

import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardNamingEntryUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardNamingEntryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.page.PageData;

/** Workspace naming dictionary: the root words a layer's table-name rule matches. */
public interface StandardNamingEntryService {
    /**
     * Reads one page of entries.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param keyword search text, or null
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return ordered entries and the matching total
     */
    PageData<StandardNamingEntryVO> page(String workspaceId, String actor, String keyword, int pageNo, int pageSize);
    /**
     * Creates an entry.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input editable attributes
     * @return committed identity and revision
     */
    StandardSavedVO create(String workspaceId, String actor, StandardNamingEntryInputDTO input);
    /**
     * Replaces entry attributes under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes and expected revision
     * @return committed identity and incremented revision
     */
    StandardSavedVO update(String workspaceId, String actor, String id, StandardNamingEntryUpdateDTO input);
    /**
     * Deletes an entry; models named under it restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision editor's expected revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
    /**
     * Checks Workspace abbreviation availability after authorizing any excluded record.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param enCode exact candidate abbreviation
     * @param excludeId edited identity, or null
     * @return true when no other entry owns the abbreviation
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Reads the models whose name was composed from one entry.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the reference list
     */
    StandardReferencesVO references(String workspaceId, String actor, String id);
}
