package io.yak.ops.common.bean.vo.governance;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Definition of one compiled rule.
 *
 * <p>{@code thresholds} is keyed by a stable name; no localized sentence is returned.
 */
@Getter
@Setter
public class GovernanceRuleVO {

    private String code;

    private Integer version;

    /** DEV or COMPUTE. */
    private String dimension;

    private String severity;

    /** Weight the severity adds to the weighted Issue count: 10, 3 or 1. */
    private Integer weight;

    /** DATA_MAP_TABLE or TASK_DEFINITION. */
    private String targetType;

    private Map<String, BigDecimal> thresholds;
}
