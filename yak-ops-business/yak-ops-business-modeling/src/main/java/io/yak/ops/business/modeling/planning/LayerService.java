package io.yak.ops.business.modeling.planning;

import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.bean.vo.modeling.LayerDataSourceVO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.bean.vo.modeling.LayerVO;
import java.util.List;

/**
 * Workspace data warehouse layers: five undeletable presets plus custom layers bound to a physical datasource.
 *
 * <p>Enabling a layer requires a committed datasource binding so physical publishing can resolve an instance.
 */
public interface LayerService {
    /**
     * Reads every layer, seeding the five presets on first access.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return preset-first ordered layers
     */
    List<LayerVO> list(String workspaceId, String actor);
    /**
     * Projects only enabled layers for downstream dimension modeling selectors.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return selectable layers carrying their bound datasource
     */
    List<LayerOptionVO> options(String workspaceId, String actor);
    /**
     * Lists datasources a layer may bind inside the Workspace.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @return bindable datasources without credentials
     */
    List<LayerDataSourceVO> datasources(String workspaceId, String actor);
    /**
     * Checks Workspace-local abbreviation availability without reserving it.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param enCode candidate abbreviation, normalized to uppercase
     * @param excludeId edited layer identity, or null for creation
     * @return true when no other layer owns the abbreviation
     */
    boolean available(String workspaceId, String actor, String enCode, String excludeId);
    /**
     * Creates a custom layer with an optional datasource binding.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param input editable fields
     * @return committed layer
     */
    LayerVO create(String workspaceId, String actor, LayerInputDTO input);
    /**
     * Updates editable fields; presets keep their abbreviation, name and description.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id layer identity
     * @param input replacement fields and expected revision
     * @return committed layer with the incremented revision
     */
    LayerVO update(String workspaceId, String actor, String id, UpdateLayerDTO input);
    /**
     * Deletes a custom layer, never a preset.
     * @param workspaceId current Workspace
     * @param actor authenticated user
     * @param id layer identity
     * @param revision expected revision
     */
    void delete(String workspaceId, String actor, String id, long revision);
}
