package io.yak.ops.business.governance.service.impl;

import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.service.GovernanceHealthService;
import io.yak.ops.business.governance.support.GovernanceAssembler;
import io.yak.ops.common.bean.vo.governance.GovernanceDimensionScoreVO;
import io.yak.ops.common.bean.vo.governance.GovernanceHealthVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueCountsVO;
import io.yak.ops.common.bean.vo.governance.GovernanceOverviewVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleCountVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleVO;
import io.yak.ops.common.enums.governance.GovernanceDimension;
import io.yak.ops.common.enums.governance.GovernanceErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.governance.GovernanceHealthSnapshotEntity;
import io.yak.ops.dao.mapper.governance.GovernanceHealthSnapshotMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssueMapper;
import io.yak.ops.dao.mapper.governance.GovernanceRuleCountRow;
import io.yak.ops.dao.mapper.governance.GovernanceScanRunMapper;
import io.yak.ops.dao.mapper.governance.GovernanceSeverityCountRow;
import io.yak.ops.dao.mapper.governance.GovernanceStatusCountRow;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Reads the score snapshot, the trend and the live Issue counters. */
@Service
public class GovernanceHealthServiceImpl implements GovernanceHealthService {

    /** Longest trend range the endpoint accepts. */
    private static final int MAX_RANGE_DAYS = 90;

    /** Default range when the caller omits both bounds. */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final GovernanceHealthSnapshotMapper snapshotMapper;

    private final GovernanceIssueMapper issueMapper;

    private final GovernanceScanRunMapper scanRunMapper;

    private final GovernanceRuleRegistry registry;

    private final GovernanceAssembler assembler;

    public GovernanceHealthServiceImpl(
            GovernanceHealthSnapshotMapper snapshotMapper,
            GovernanceIssueMapper issueMapper,
            GovernanceScanRunMapper scanRunMapper,
            GovernanceRuleRegistry registry,
            GovernanceAssembler assembler) {
        this.snapshotMapper = snapshotMapper;
        this.issueMapper = issueMapper;
        this.scanRunMapper = scanRunMapper;
        this.registry = registry;
        this.assembler = assembler;
    }

    @Override
    public GovernanceOverviewVO overview(String workspaceId) {
        LocalDate day = snapshotMapper.latestDate(workspaceId);
        List<GovernanceHealthSnapshotEntity> rows =
                day == null ? List.of() : snapshotMapper.listByDay(workspaceId, day);

        List<GovernanceDimensionScoreVO> dimensions = new ArrayList<>(2);
        GovernanceDimensionScoreVO overall = null;
        String formulaVersion = null;
        for (GovernanceDimensionScoreVO row :
                rows.stream().map(assembler::toDimension).toList()) {
            formulaVersion = formulaVersion == null ? formulaVersionOf(rows, row.getDimensionCode()) : formulaVersion;
            if (GovernanceDimension.OVERALL.getValue().equals(row.getDimensionCode())) {
                overall = row;
            } else {
                dimensions.add(row);
            }
        }

        GovernanceOverviewVO overview = new GovernanceOverviewVO();
        overview.setDimensions(dimensions);
        overview.setOverall(overall);
        overview.setEvaluatedDimensionCount((int) dimensions.stream()
                .filter(d -> Boolean.TRUE.equals(d.getEvaluated()))
                .count());
        overview.setSnapshotDate(day == null ? null : day.toString());
        overview.setFormulaVersion(formulaVersion);
        overview.setIssueCounts(issueCounts(workspaceId));
        overview.setRuleCounts(ruleCounts(workspaceId));
        overview.setLastScan(scanRunMapper.listRecent(workspaceId, 1).stream()
                .findFirst()
                .map(assembler::toScan)
                .orElse(null));
        return overview;
    }

    @Override
    public GovernanceHealthVO health(String workspaceId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from == null ? today.minusDays(DEFAULT_RANGE_DAYS) : from;
        LocalDate end = to == null ? today : to;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw new BusinessException(GovernanceErrorCode.INVALID_DATE_RANGE);
        }
        GovernanceHealthVO health = new GovernanceHealthVO();
        health.setFrom(start.toString());
        health.setTo(end.toString());
        health.setPoints(snapshotMapper.listByRange(workspaceId, start, end).stream()
                .map(assembler::toPoint)
                .toList());
        return health;
    }

    @Override
    public List<GovernanceRuleVO> rules() {
        return registry.rules().stream().map(assembler::toRule).toList();
    }

    private String formulaVersionOf(List<GovernanceHealthSnapshotEntity> rows, String dimensionCode) {
        return rows.stream()
                .filter(row -> dimensionCode.equals(row.getDimensionCode()))
                .map(GovernanceHealthSnapshotEntity::getFormulaVersion)
                .findFirst()
                .orElse(null);
    }

    /**
     * Live counts: statuses over every Issue, severities over the score-bearing ones.
     *
     * <p>Severity counts add the two dimensions together, so they match the basis of the score rather
     * than the raw board.
     */
    private GovernanceIssueCountsVO issueCounts(String workspaceId) {
        GovernanceIssueCountsVO vo = new GovernanceIssueCountsVO();
        vo.setTotal(0);
        vo.setOpen(0);
        vo.setHandling(0);
        vo.setIgnored(0);
        vo.setResolved(0);
        for (GovernanceStatusCountRow row : issueMapper.countByStatus(workspaceId)) {
            int total = row.getTotal() == null ? 0 : row.getTotal();
            vo.setTotal(vo.getTotal() + total);
            switch (row.getStatus()) {
                case "OPEN" -> vo.setOpen(total);
                case "HANDLING" -> vo.setHandling(total);
                case "IGNORED" -> vo.setIgnored(total);
                case "RESOLVED" -> vo.setResolved(total);
                default -> {
                    // An unknown status cannot be produced by the check constraint.
                }
            }
        }
        Map<String, Integer> severity = new LinkedHashMap<>();
        severity.put("HIGH", 0);
        severity.put("MEDIUM", 0);
        severity.put("LOW", 0);
        for (GovernanceDimension dimension : List.of(GovernanceDimension.DEV, GovernanceDimension.COMPUTE)) {
            for (GovernanceSeverityCountRow row :
                    issueMapper.countScoredBySeverity(workspaceId, dimension.getValue())) {
                severity.merge(row.getSeverity(), row.getTotal() == null ? 0 : row.getTotal(), Integer::sum);
            }
        }
        vo.setHigh(severity.get("HIGH"));
        vo.setMedium(severity.get("MEDIUM"));
        vo.setLow(severity.get("LOW"));
        return vo;
    }

    private List<GovernanceRuleCountVO> ruleCounts(String workspaceId) {
        List<GovernanceRuleCountVO> counts = new ArrayList<>();
        for (GovernanceRuleCountRow row : issueMapper.countScoredByRule(workspaceId)) {
            GovernanceRuleCountVO vo = new GovernanceRuleCountVO();
            vo.setRuleCode(row.getRuleCode());
            vo.setCount(row.getTotal());
            counts.add(vo);
        }
        return counts;
    }
}
