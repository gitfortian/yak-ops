package io.yak.ops.business.governance.score;

import io.yak.ops.business.datamap.governance.DataMapGovernanceQuery;
import io.yak.ops.business.task.governance.TaskGovernanceQuery;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.IssueSeverity;
import io.yak.ops.dao.mapper.governance.GovernanceIssueMapper;
import io.yak.ops.dao.mapper.governance.GovernanceSeverityCountRow;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Derives the current facts of both dimensions.
 *
 * <p>The scan writes the facts into a snapshot, and the overview reads the snapshot. Recomputing
 * here rather than reading the last score keeps a manual transition from waiting for tomorrow's run
 * before the operator sees its effect on the counters.
 */
@Component
public class GovernanceScoreCalculator {

    /** Window the COMPUTE base and both Task rules look back over. */
    public static final int COMPUTE_WINDOW_DAYS = 7;

    private final DataMapGovernanceQuery dataMap;

    private final TaskGovernanceQuery tasks;

    private final GovernanceIssueMapper issueMapper;

    public GovernanceScoreCalculator(
            DataMapGovernanceQuery dataMap, TaskGovernanceQuery tasks, GovernanceIssueMapper issueMapper) {
        this.dataMap = dataMap;
        this.tasks = tasks;
        this.issueMapper = issueMapper;
    }

    /** Facts of the DEV dimension: active catalog tables and their ownership Issues. */
    public GovernanceDimensionFacts dev(String workspaceId) {
        return facts(workspaceId, GovernanceDimension.DEV, dataMap.countActiveTables(workspaceId));
    }

    /** Facts of the COMPUTE dimension: published definitions that ran a scheduled instance recently. */
    public GovernanceDimensionFacts compute(String workspaceId, LocalDateTime now) {
        long base = tasks.countEvaluatedDefinitions(workspaceId, now.minusDays(COMPUTE_WINDOW_DAYS));
        return facts(workspaceId, GovernanceDimension.COMPUTE, base);
    }

    private GovernanceDimensionFacts facts(String workspaceId, GovernanceDimension dimension, long base) {
        Map<IssueSeverity, Integer> counts = new EnumMap<>(IssueSeverity.class);
        int total = 0;
        int weighted = 0;
        for (GovernanceSeverityCountRow row : issueMapper.countScoredBySeverity(workspaceId, dimension.getValue())) {
            IssueSeverity severity = IssueSeverity.valueOf(row.getSeverity());
            int count = row.getTotal() == null ? 0 : row.getTotal();
            counts.merge(severity, count, Integer::sum);
            total += count;
            weighted += count * severity.getWeight();
        }
        int baseCount = base > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) base;
        return GovernanceDimensionFacts.of(dimension, baseCount, weighted, total, counts);
    }
}
