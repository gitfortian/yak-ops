package io.yak.ops.business.modeling.standard;

import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardMeasureUnitUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardMeasureUnitVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
import io.yak.ops.common.page.PageData;

/** Workspace measurement units: a closed taxonomy annotated onto fields and atomic metrics. */
public interface StandardMeasureUnitService {
    /**
     * Reads one page of units.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param category fixed category, or null for every category
     * @param keyword search text, or null
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return ordered units and the matching total
     */
    PageData<StandardMeasureUnitVO> page(
            String workspaceId, String actor, MeasureUnitCategory category, String keyword, int pageNo, int pageSize);
    /**
     * Creates a unit.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input editable attributes
     * @return committed identity and revision
     */
    StandardSavedVO create(String workspaceId, String actor, StandardMeasureUnitInputDTO input);
    /**
     * Replaces unit attributes under the observed revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input replacement attributes and expected revision
     * @return committed identity and incremented revision
     */
    StandardSavedVO update(String workspaceId, String actor, String id, StandardMeasureUnitUpdateDTO input);
    /**
     * Deletes a unit; inbound references restrict deletion.
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
     * @return true when no other unit owns the abbreviation
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Reads the field standards and model fields that reference one unit.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the reference list
     */
    StandardReferencesVO references(String workspaceId, String actor, String id);
}
