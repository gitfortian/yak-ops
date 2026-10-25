package io.yak.ops.business.modeling.naming;

import io.yak.ops.common.bean.dto.modeling.NamingRuleUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.LayerNamingRuleVO;

/** Reads, toggles and applies the table-name rule of one warehouse layer. */
public interface NamingRuleService {
    /**
     * Reads the rule state and the models that currently do not conform.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param layerId layer identity
     * @return the toggle, its revision and the non-conforming models
     */
    LayerNamingRuleVO rule(String workspaceId, String actor, String layerId);
    /**
     * Enables or disables the rule under the observed revision.
     *
     * <p>Enabling requires a non-empty dictionary, a head that fits the suffix budget under every
     * process of the layer, and an explicit acknowledgement while non-conforming models remain.
     * Conforming models record the entry their name was composed from.
     *
     * @param workspaceId authorized current Workspace
     * @param actor authenticated user
     * @param layerId layer identity
     * @param input requested state, acknowledgement and expected revision
     * @return the committed state
     */
    LayerNamingRuleVO update(String workspaceId, String actor, String layerId, NamingRuleUpdateDTO input);
    /**
     * Resolves the dictionary entry a model suffix is composed from.
     * @param workspaceId authorized current Workspace
     * @param suffix submitted model suffix without the layer prefix
     * @param processCode code of the model's own business process
     * @return the matched entry identity
     */
    String rootWord(String workspaceId, String suffix, String processCode);
}
