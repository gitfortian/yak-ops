package io.yak.ops.business.governance.rule.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.modeling.governance.ModelingGovernanceQuery;
import io.yak.ops.business.modeling.governance.ModelingGovernanceRow;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Grain and relation-key rules: one candidate per model, never a save-time block. */
class ModelingRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

    private final ModelingGovernanceQuery modeling = mock(ModelingGovernanceQuery.class);

    private GovernanceRuleContext context() {
        return new GovernanceRuleContext("ws-1", "scan-1", NOW, Mockito.mock(io.yak.ops.business.datamap.governance.DataMapGovernanceQuery.class),
                Mockito.mock(io.yak.ops.business.task.governance.TaskGovernanceQuery.class), modeling);
    }

    @Test
    void grainRuleAccusesOnlyModelsWithoutGrain() {
        ModelingGrainUndeclaredRule rule = new ModelingGrainUndeclaredRule();
        when(modeling.modelsMissingGrain("ws-1"))
                .thenReturn(List.of(new ModelingGovernanceRow("m-1", "订单明细"), new ModelingGovernanceRow("m-2", "汇总")));

        List<IssueCandidate> candidates = new java.util.ArrayList<>();
        rule.evaluate(context(), candidates::add);

        assertEquals(2, candidates.size());
        assertEquals("订单明细", candidates.get(0).targetName());
        assertEquals(IssueSeverity.MEDIUM, rule.severity());
        assertEquals(IssueTargetType.MODELING_MODEL, rule.targetType());
        assertFalse(rule.scored(), "grain findings are advisory and must not enter the health score");
    }

    @Test
    void relationKeyRuleDeduplicatesByModel() {
        ModelRelationKeyUnstandardizedRule rule = new ModelRelationKeyUnstandardizedRule();
        when(modeling.modelsWithUnstandardizedRelationKeys("ws-1"))
                .thenReturn(List.of(
                        new ModelingGovernanceRow("m-1", "订单明细"),
                        new ModelingGovernanceRow("m-1", "订单明细"),
                        new ModelingGovernanceRow("m-2", "客户维表")));

        List<IssueCandidate> candidates = new java.util.ArrayList<>();
        rule.evaluate(context(), candidates::add);

        assertEquals(2, candidates.size());
        assertEquals(
                java.util.Set.of("m-1", "m-2"),
                candidates.stream().map(IssueCandidate::targetId).collect(Collectors.toSet()));
        assertEquals(IssueSeverity.HIGH, rule.severity());
        assertFalse(rule.scored(), "relation-key findings are advisory and must not enter the health score");
    }
}
