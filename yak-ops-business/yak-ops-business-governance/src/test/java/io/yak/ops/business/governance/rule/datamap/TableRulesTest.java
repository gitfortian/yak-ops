package io.yak.ops.business.governance.rule.datamap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.datamap.governance.GovernanceTableSnapshot;
import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the two catalog rules, including the exclusions their semantics depend on. */
class TableRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 9, 12);

    private final DataMapGovernanceQuery dataMap = mock(DataMapGovernanceQuery.class);
    private final TaskGovernanceQuery tasks = mock(TaskGovernanceQuery.class);

    private GovernanceRuleContext context() {
        return new GovernanceRuleContext("ws-1", "scan-1", NOW, dataMap, tasks);
    }

    private void tables(GovernanceTableSnapshot... rows) {
        when(dataMap.activeTables(eq("ws-1"), anyString(), anyInt())).thenReturn(List.of(rows));
    }

    private static List<IssueCandidate> collect(GovernanceRule rule, GovernanceRuleContext context) {
        List<IssueCandidate> found = new ArrayList<>();
        rule.evaluate(context, found::add);
        return found;
    }

    @Test
    void accusesOnlyTablesWithoutAnOwner() {
        tables(
                new GovernanceTableSnapshot("t1", "ods.orders", "订单", "订单表", ""),
                new GovernanceTableSnapshot("t2", "ods.users", "用户", "用户表", "u-9"));

        List<IssueCandidate> found = collect(new TableOwnerRule(), context());

        assertEquals(1, found.size());
        assertEquals("t1", found.get(0).targetId());
        assertEquals("ods.orders", found.get(0).targetName());
        assertEquals(0, found.get(0).thresholdValue().compareTo(BigDecimal.ONE));
    }

    @Test
    void aBlankOwnerIsTreatedAsMissing() {
        tables(new GovernanceTableSnapshot("t1", "ods.orders", "订单", "订单表", "   "));
        assertEquals(1, collect(new TableOwnerRule(), context()).size());
    }

    @Test
    void aCatalogCommentCountsAsADescription() {
        tables(
                new GovernanceTableSnapshot("t1", "ods.orders", null, "订单主表", ""),
                new GovernanceTableSnapshot("t2", "ods.users", " ", null, "u-9"));

        List<IssueCandidate> found = collect(new TableDescriptionRule(), context());

        assertEquals(1, found.size());
        assertEquals("t2", found.get(0).targetId());
    }

    @Test
    void anEmptyCatalogProducesNoCandidates() {
        tables();
        assertTrue(collect(new TableOwnerRule(), context()).isEmpty());
    }
}
