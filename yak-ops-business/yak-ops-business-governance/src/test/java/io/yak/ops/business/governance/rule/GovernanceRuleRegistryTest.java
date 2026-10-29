package io.yak.ops.business.governance.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.governance.rule.datamap.TableDescriptionRule;
import io.yak.ops.business.governance.rule.datamap.TableOwnerRule;
import io.yak.ops.business.governance.rule.task.TaskConsecutiveFailuresRule;
import io.yak.ops.business.governance.rule.task.TaskSlowRunsRule;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Guards the registry invariants that would otherwise surface only at startup. */
class GovernanceRuleRegistryTest {

    @Test
    void registersTheFourCompiledRules() {
        GovernanceRuleRegistry registry = new GovernanceRuleRegistry(List.of(
                new TableOwnerRule(), new TableDescriptionRule(),
                new TaskConsecutiveFailuresRule(), new TaskSlowRunsRule()));

        assertEquals(4, registry.rules().size());
        assertEquals(IssueSeverity.MEDIUM, registry.require("TASK_SLOW_RUNS").severity());
    }

    @Test
    void rejectsADuplicateCode() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new GovernanceRuleRegistry(List.of(new TableOwnerRule(), new TableOwnerRule())));
        assertTrue(failure.getMessage().contains("Duplicate"), failure.getMessage());
    }

    @Test
    void rejectsABlankCodeOrAVersionBelowOne() {
        assertThrows(IllegalStateException.class, () -> new GovernanceRuleRegistry(List.of(rule("", 1))));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new GovernanceRuleRegistry(List.of(rule("X", 0))));
        assertTrue(failure.getMessage().contains("version"), failure.getMessage());
    }

    private static GovernanceRule rule(String code, int version) {
        return new GovernanceRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public GovernanceDimension dimension() {
                return GovernanceDimension.DEV;
            }

            @Override
            public IssueSeverity severity() {
                return IssueSeverity.LOW;
            }

            @Override
            public IssueTargetType targetType() {
                return IssueTargetType.DATA_MAP_TABLE;
            }

            @Override
            public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {}
        };
    }
}
