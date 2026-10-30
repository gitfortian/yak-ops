package io.yak.ops.business.governance.rule;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import java.time.LocalDateTime;

/**
 * Read-only inputs of one rule evaluation.
 *
 * @param workspaceId Workspace the run was authorised for
 * @param scanId run identity persisted on every Issue the rule touches
 * @param evaluatedAt instant shared by every rule of the run
 * @param dataMap read port owned by the data map
 * @param tasks read port owned by the Task module
 */
public record GovernanceRuleContext(
        String workspaceId,
        String scanId,
        LocalDateTime evaluatedAt,
        DataMapGovernanceQuery dataMap,
        TaskGovernanceQuery tasks) {}
