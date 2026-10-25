package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/**
 * State of one layer's naming rule.
 *
 * @param layerId layer identity
 * @param enabled whether model suffixes must carry a dictionary root word and the process
 * @param revision current optimistic version of the layer
 * @param violations DWD and DWS models whose current suffix does not conform; read on demand, not stored
 */
public record LayerNamingRuleVO(String layerId, boolean enabled, long revision, List<NamingViolationVO> violations) {
    public LayerNamingRuleVO {
        violations = List.copyOf(violations);
    }
}
