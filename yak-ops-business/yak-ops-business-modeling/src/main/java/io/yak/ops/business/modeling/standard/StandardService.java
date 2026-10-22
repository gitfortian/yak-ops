package io.yak.ops.business.modeling.standard;

import io.yak.ops.common.bean.dto.modeling.standard.CategoryInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.CategoryUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardInputDTO;
import io.yak.ops.common.bean.dto.modeling.standard.StandardUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardCategoryVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardFieldVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardReferencesVO;
import io.yak.ops.common.bean.vo.modeling.standard.StandardSavedVO;
import io.yak.ops.common.page.PageData;
import java.util.List;

/** Workspace-authorized catalog and standard field operations. */
public interface StandardService {
    /**
     * Initializes default directories once under the Workspace catalog lock.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @return the complete current directory tree
     */
    List<StandardCategoryVO> initialize(String workspaceId, String actor);
    /**
     * Reads a directory snapshot without creating default records.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @return the complete directory tree
     */
    List<StandardCategoryVO> tree(String workspaceId, String actor);
    /**
     * Projects active Workspace members without private account attributes.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @return eligible owner identities and display names
     */
    List<OwnerOptionVO> owners(String workspaceId, String actor);
    /**
     * Creates an immutable-parent directory within the supported depth.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param input editable fields and expected revision where applicable
     * @return committed identity and revision
     */
    StandardSavedVO createCategory(String workspaceId, String actor, CategoryInputDTO input);
    /**
     * Renames a directory at the editor's expected revision.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input editable fields and expected revision where applicable
     * @return committed identity and incremented revision
     */
    StandardSavedVO renameCategory(String workspaceId, String actor, String id, CategoryUpdateDTO input);
    /**
     * Deletes a dependency-free directory under the catalog lock.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision editor's expected revision
     */
    void deleteCategory(String workspaceId, String actor, String id, long revision);
    /**
     * Reads a category page or global search in one consistent snapshot.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param categoryId fixed category, or null for global search
     * @param keyword global search text, or null for category reads
     * @param pageNo one-based page number
     * @param pageSize page size from 1 to 100
     * @return ordered fields and matching total
     */
    PageData<StandardFieldVO> page(
            String workspaceId, String actor, String categoryId, String keyword, int pageNo, int pageSize);
    /**
     * Reads a field after verifying Workspace ownership.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return the field and its current category path
     */
    StandardFieldVO field(String workspaceId, String actor, String id);
    /**
     * Creates a field with an immutable category and globally unique code.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param categoryId owning category in the current Workspace
     * @param input editable fields and expected revision where applicable
     * @return committed identity and revision
     */
    StandardSavedVO createField(String workspaceId, String actor, String categoryId, StandardInputDTO input);
    /**
     * Replaces editable attributes at an expected revision; code conflicts roll back.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param input editable fields and expected revision where applicable
     * @return committed identity and incremented revision
     */
    StandardSavedVO updateField(String workspaceId, String actor, String id, StandardUpdateDTO input);
    /**
     * Deletes a field at an expected revision; inbound references restrict deletion.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @param revision editor's expected revision
     */
    void deleteField(String workspaceId, String actor, String id, long revision);
    /**
     * Checks global code availability after authorizing any excluded record.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param enCode exact candidate code
     * @param excludeId edited standard identity, or null
     * @return true when no other standard owns the code
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Authorizes the field before reporting logical references as not enabled.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param id target identity
     * @return an explicit unavailable reference state
     */
    StandardReferencesVO references(String workspaceId, String actor, String id);
}
