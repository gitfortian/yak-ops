package io.yak.ops.business.governance.scan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.rule.IssueCandidate;
import io.yak.ops.business.governance.score.GovernanceDimensionFacts;
import io.yak.ops.business.governance.score.GovernanceScoreCalculator;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.GovernanceScanStatus;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.common.enums.governance.IssueTargetType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Pins the convergence rules of a run: the data map gate, partial outcomes and snapshots. */
class GovernanceScanExecutorTest {

    private static final String WORKSPACE = "ws-1";

    private static final String SCAN = "scan-1";

    private final GovernanceScanWriter writer = mock(GovernanceScanWriter.class);

    private final GovernanceScoreCalculator calculator = mock(GovernanceScoreCalculator.class);

    private final DataMapGovernanceQuery dataMap = mock(DataMapGovernanceQuery.class);

    private final TaskGovernanceQuery tasks = mock(TaskGovernanceQuery.class);

    private GovernanceScanExecutor executor(GovernanceRule... rules) {
        when(writer.claim(eq(WORKSPACE), eq(SCAN), any())).thenReturn(true);
        when(writer.startRule(eq(WORKSPACE), eq(SCAN), any(), any())).thenReturn("result-1");
        return new GovernanceScanExecutor(
                new GovernanceRuleRegistry(List.of(rules)), writer, calculator, dataMap, tasks);
    }

    private static GovernanceRule rule(String code, Consumer<Consumer<IssueCandidate>> body) {
        return new GovernanceRule() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public GovernanceDimension dimension() {
                return GovernanceDimension.DEV;
            }

            @Override
            public IssueSeverity severity() {
                return IssueSeverity.HIGH;
            }

            @Override
            public IssueTargetType targetType() {
                return IssueTargetType.DATA_MAP_TABLE;
            }

            @Override
            public void evaluate(GovernanceRuleContext context, Consumer<IssueCandidate> sink) {
                body.accept(sink);
            }
        };
    }

    private static IssueCandidate candidate(String targetId) {
        return new IssueCandidate(targetId, "ods." + targetId, BigDecimal.ZERO, BigDecimal.ONE, 1, 0);
    }

    private static GovernanceDimensionFacts facts(GovernanceDimension dimension, int base, int issues) {
        Map<IssueSeverity, Integer> counts = new EnumMap<>(IssueSeverity.class);
        counts.put(IssueSeverity.HIGH, issues);
        return GovernanceDimensionFacts.of(dimension, base, issues * 10, issues, counts);
    }

    @Test
    void skipsTheRunWhileTheDataMapCrawls() {
        when(dataMap.hasActiveCrawl(WORKSPACE)).thenReturn(true);
        executor(rule("R1", sink -> sink.accept(candidate("t1")))).execute(WORKSPACE, SCAN);

        verify(writer)
                .finish(
                        eq(WORKSPACE),
                        eq(SCAN),
                        eq(GovernanceScanStatus.SKIPPED),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        eq("DATA_MAP_BUSY"),
                        any(LocalDateTime.class));
        verify(writer, never()).openExpiredIgnores(anyString(), any());
        verify(writer, never()).startRule(anyString(), anyString(), any(), any());
        verify(writer, never()).writeSnapshot(anyString(), anyString(), any(), anyList(), any(), any());
    }

    @Test
    void aRuleThatThrowsResolvesNothingAndNeverWritesASnapshot() {
        when(dataMap.hasActiveCrawl(WORKSPACE)).thenReturn(false);
        GovernanceRule broken = rule("BROKEN", sink -> {
            throw new IllegalStateException("boom");
        });

        executor(broken).execute(WORKSPACE, SCAN);

        verify(writer, never()).autoResolveUnobserved(anyString(), anyString(), anyString(), any());
        verify(writer)
                .completeRule(
                        eq(WORKSPACE),
                        eq("result-1"),
                        eq("FAILED"),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        eq(0),
                        eq("RULE_FAILED"),
                        any(LocalDateTime.class));
        verify(writer)
                .finish(
                        eq(WORKSPACE),
                        eq(SCAN),
                        eq(GovernanceScanStatus.FAILED),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.isNull(),
                        any(LocalDateTime.class));
        verify(writer, never()).writeSnapshot(anyString(), anyString(), any(), anyList(), any(), any());
    }

    @Test
    void aCompleteRunClosesUnobservedFindingsAndWritesTheSnapshot() {
        when(dataMap.hasActiveCrawl(WORKSPACE)).thenReturn(false);
        when(writer.applyBatch(eq(WORKSPACE), eq(SCAN), any(), anyList(), any()))
                .thenReturn(new GovernanceScanWriter.BatchCounts(1, 0));
        when(writer.autoResolveUnobserved(eq(WORKSPACE), eq(SCAN), eq("R1"), any())).thenReturn(2);
        when(calculator.dev(WORKSPACE)).thenReturn(facts(GovernanceDimension.DEV, 10, 1));
        when(calculator.compute(eq(WORKSPACE), any())).thenReturn(facts(GovernanceDimension.COMPUTE, 0, 0));

        executor(rule("R1", sink -> sink.accept(candidate("t1")))).execute(WORKSPACE, SCAN);

        verify(writer).applyBatch(eq(WORKSPACE), eq(SCAN), any(), anyList(), any());
        verify(writer).autoResolveUnobserved(eq(WORKSPACE), eq(SCAN), eq("R1"), any());
        verify(writer)
                .finish(
                        eq(WORKSPACE),
                        eq(SCAN),
                        eq(GovernanceScanStatus.SUCCEEDED),
                        eq(1),
                        eq(0),
                        eq(1),
                        eq(0),
                        eq(2),
                        org.mockito.ArgumentMatchers.isNull(),
                        any(LocalDateTime.class));
        // COMPUTE has no eligible base, so the run writes no OVERALL row.
        verify(writer)
                .writeSnapshot(
                        eq(WORKSPACE),
                        eq(SCAN),
                        any(),
                        anyList(),
                        org.mockito.ArgumentMatchers.isNull(),
                        any(LocalDateTime.class));
    }

    @Test
    void aRunAnotherWorkerClaimedDoesNothing() {
        GovernanceScanExecutor executor =
                new GovernanceScanExecutor(new GovernanceRuleRegistry(List.of()), writer, calculator, dataMap, tasks);
        when(writer.claim(eq(WORKSPACE), eq(SCAN), any())).thenReturn(false);

        executor.execute(WORKSPACE, SCAN);

        verify(dataMap, never()).hasActiveCrawl(anyString());
        verify(writer, never()).finish(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyInt(),
                anyInt(), any(), any());
    }
}
