package io.yak.ops.business.governance.support;

import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.common.bean.vo.governance.GovernanceDimensionScoreVO;
import io.yak.ops.common.bean.vo.governance.GovernanceHealthPointVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueEventVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleResultVO;
import io.yak.ops.common.bean.vo.governance.GovernanceRuleVO;
import io.yak.ops.common.bean.vo.governance.GovernanceScanVO;
import io.yak.ops.dao.entity.governance.GovernanceHealthSnapshotEntity;
import io.yak.ops.dao.entity.governance.GovernanceIssueEntity;
import io.yak.ops.dao.entity.governance.GovernanceIssueEventEntity;
import io.yak.ops.dao.entity.governance.GovernanceRuleResultEntity;
import io.yak.ops.dao.entity.governance.GovernanceScanRunEntity;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

/** Converts governance rows and registry entries into the HTTP response shapes. */
@Component
public class GovernanceAssembler {

    /** One run with its counters and wall-clock duration. */
    public GovernanceScanVO toScan(GovernanceScanRunEntity row) {
        GovernanceScanVO vo = new GovernanceScanVO();
        vo.setId(row.getId());
        vo.setTriggerType(row.getTriggerType());
        vo.setStatus(row.getStatus());
        vo.setRuleCount(row.getRuleCount());
        vo.setSucceededRuleCount(row.getSucceededRuleCount());
        vo.setFailedRuleCount(row.getFailedRuleCount());
        vo.setIssueCreatedCount(row.getIssueCreatedCount());
        vo.setIssueReopenedCount(row.getIssueReopenedCount());
        vo.setIssueResolvedCount(row.getIssueResolvedCount());
        vo.setReasonCode(row.getReasonCode());
        vo.setStartTime(row.getStartTime());
        vo.setFinishTime(row.getFinishTime());
        vo.setCreateTime(row.getCreateTime());
        vo.setCreateBy(row.getCreateBy());
        if (row.getStartTime() != null && row.getFinishTime() != null) {
            vo.setDurationMillis(
                    Duration.between(row.getStartTime(), row.getFinishTime()).toMillis());
        }
        return vo;
    }

    /** One per-rule outcome of a run. */
    public GovernanceRuleResultVO toRuleResult(GovernanceRuleResultEntity row) {
        GovernanceRuleResultVO vo = new GovernanceRuleResultVO();
        vo.setRuleCode(row.getRuleCode());
        vo.setRuleVersion(row.getRuleVersion());
        vo.setStatus(row.getStatus());
        vo.setFindingCount(row.getFindingCount());
        vo.setIssueCreatedCount(row.getIssueCreatedCount());
        vo.setIssueReopenedCount(row.getIssueReopenedCount());
        vo.setIssueResolvedCount(row.getIssueResolvedCount());
        vo.setReasonCode(row.getReasonCode());
        vo.setStartTime(row.getStartTime());
        vo.setFinishTime(row.getFinishTime());
        return vo;
    }

    /** One Issue with numeric evidence and no localized text. */
    public GovernanceIssueVO toIssue(GovernanceIssueEntity row) {
        GovernanceIssueVO vo = new GovernanceIssueVO();
        vo.setId(row.getId());
        vo.setRuleCode(row.getRuleCode());
        vo.setRuleVersion(row.getRuleVersion());
        vo.setDimension(row.getDimensionCode());
        vo.setSeverity(row.getSeverity());
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setTargetName(row.getTargetName());
        vo.setStatus(row.getStatus());
        vo.setOwnerId(row.getOwnerId());
        vo.setIgnoreUntil(row.getIgnoreUntil());
        vo.setResolvedAt(row.getResolvedAt());
        vo.setResolvedBy(row.getResolvedBy());
        vo.setResolution(row.getResolution());
        vo.setObservedValue(row.getObservedValue());
        vo.setThresholdValue(row.getThresholdValue());
        vo.setSampleCount(row.getSampleCount());
        vo.setWindowDays(row.getWindowDays());
        vo.setFirstDetectedAt(row.getFirstDetectedAt());
        vo.setLastDetectedAt(row.getLastDetectedAt());
        vo.setLastScanId(row.getLastScanId());
        vo.setRevision(row.getRevision());
        return vo;
    }

    /** One transition of an Issue. */
    public GovernanceIssueEventVO toEvent(GovernanceIssueEventEntity row) {
        GovernanceIssueEventVO vo = new GovernanceIssueEventVO();
        vo.setId(row.getId());
        vo.setEventType(row.getEventType());
        vo.setFromStatus(row.getFromStatus());
        vo.setToStatus(row.getToStatus());
        vo.setOwnerId(row.getOwnerId());
        vo.setIgnoreUntil(row.getIgnoreUntil());
        vo.setNote(row.getNote());
        vo.setActorId(row.getActorId());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /** One compiled rule, including the numeric thresholds its evaluation used. */
    public GovernanceRuleVO toRule(GovernanceRule rule) {
        GovernanceRuleVO vo = new GovernanceRuleVO();
        vo.setCode(rule.code());
        vo.setVersion(rule.version());
        vo.setDimension(rule.dimension().getValue());
        vo.setSeverity(rule.severity().getValue());
        vo.setWeight(rule.severity().getWeight());
        vo.setTargetType(rule.targetType().getValue());
        vo.setThresholds(new LinkedHashMap<>(rule.thresholds()));
        return vo;
    }

    /** One snapshot row as a dimension score. */
    public GovernanceDimensionScoreVO toDimension(GovernanceHealthSnapshotEntity row) {
        GovernanceDimensionScoreVO vo = new GovernanceDimensionScoreVO();
        vo.setDimensionCode(row.getDimensionCode());
        vo.setScore(row.getScore());
        vo.setBaseCount(row.getBaseCount());
        vo.setWeightedIssueCount(row.getWeightedIssueCount());
        vo.setIssueCount(row.getIssueCount());
        vo.setHighCount(row.getHighCount());
        vo.setMediumCount(row.getMediumCount());
        vo.setLowCount(row.getLowCount());
        vo.setEvaluated(row.getBaseCount() != null && row.getBaseCount() > 0);
        return vo;
    }

    /** One snapshot row as a trend point. */
    public GovernanceHealthPointVO toPoint(GovernanceHealthSnapshotEntity row) {
        GovernanceHealthPointVO vo = new GovernanceHealthPointVO();
        vo.setSnapshotDate(
                row.getSnapshotDate() == null ? null : row.getSnapshotDate().toString());
        vo.setDimensionCode(row.getDimensionCode());
        vo.setScore(row.getScore());
        vo.setBaseCount(row.getBaseCount());
        vo.setWeightedIssueCount(row.getWeightedIssueCount());
        vo.setIssueCount(row.getIssueCount());
        vo.setHighCount(row.getHighCount());
        vo.setMediumCount(row.getMediumCount());
        vo.setLowCount(row.getLowCount());
        return vo;
    }
}
