package io.yak.ops.business.datamap.tagging;

import io.yak.ops.common.bean.dto.tag.TagBindingBatchDTO;
import io.yak.ops.common.bean.dto.tag.TagKeyRenameDTO;
import io.yak.ops.common.bean.dto.tag.TagKeySaveDTO;
import io.yak.ops.common.bean.dto.tag.TagValueSaveDTO;
import io.yak.ops.common.bean.vo.tag.TagKeyVO;
import io.yak.ops.common.bean.vo.tag.TagValueVO;
import java.util.List;

/**
 * Owns the Workspace tag dictionary and the bindings that attach its values to catalog assets.
 *
 * <p>The dictionary is two-level: a key names a dimension, a value names a term inside it, and a
 * binding attaches one value to one asset. Dictionary writes require a Workspace manager; binding
 * writes are open to every member. Delete protection is a schema constraint, so a bound value or key
 * fails the delete with {@code TAG_IN_USE} instead of silently detaching.
 */
public interface TagService {

    /**
     * Lists the whole dictionary of the Workspace, ordered by key name then value name.
     *
     * @param workspaceId current Workspace
     * @return every key with its values and usage counts
     */
    List<TagKeyVO> listKeys(String workspaceId);

    /**
     * Creates a key with its optional initial values in one transaction.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param input key name and initial values
     * @return the created key with its values
     */
    TagKeyVO createKey(String workspaceId, String actor, TagKeySaveDTO input);

    /**
     * Renames a key; every binding follows because bindings reference value ids.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param keyId key identity
     * @param input new name
     * @return the renamed key with its values
     */
    TagKeyVO renameKey(String workspaceId, String actor, String keyId, TagKeyRenameDTO input);

    /**
     * Deletes a key and its values; fails with {@code TAG_IN_USE} while any value is bound.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param keyId key identity
     */
    void deleteKey(String workspaceId, String actor, String keyId);

    /**
     * Adds a value to an existing key.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param keyId key identity
     * @param input value name
     * @return the created value
     */
    TagValueVO addValue(String workspaceId, String actor, String keyId, TagValueSaveDTO input);

    /**
     * Renames a value; every binding follows because bindings reference the value id.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param valueId value identity
     * @param input new name
     * @return the renamed value
     */
    TagValueVO renameValue(String workspaceId, String actor, String valueId, TagValueSaveDTO input);

    /**
     * Deletes a value; fails with {@code TAG_IN_USE} while it is bound to at least one asset.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated manager
     * @param valueId value identity
     */
    void deleteValue(String workspaceId, String actor, String valueId);

    /**
     * Replaces the whole tag value set of one asset, all-or-nothing.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated member
     * @param tableId asset identity
     * @param tagValueIds values that remain attached; an empty list clears the asset
     */
    void replaceTableValues(String workspaceId, String actor, String tableId, List<String> tagValueIds);

    /**
     * Applies a batch binding command over many assets in one transaction.
     *
     * @param workspaceId current Workspace
     * @param actor authenticated member
     * @param input table ids, value ids and the ADD or REMOVE direction
     * @return the number of bindings the command wrote or removed
     */
    int applyBatch(String workspaceId, String actor, TagBindingBatchDTO input);
}
