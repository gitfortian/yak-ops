package io.yak.ops.business.governance.service.impl;

import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.scan.GovernanceScanDispatcher;
import io.yak.ops.business.governance.scan.GovernanceScanExecutor;
import io.yak.ops.business.governance.scan.GovernanceScanWriter;
import io.yak.ops.business.governance.service.GovernanceScanService;
import io.yak.ops.business.governance.support.GovernanceAssembler;
import io.yak.ops.common.bean.vo.governance.GovernanceScanDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceScanVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.governance.GovernanceErrorCode;
import io.yak.ops.common.enums.governance.GovernanceScanTrigger;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.dao.entity.governance.GovernanceScanRunEntity;
import io.yak.ops.dao.entity.workspace.WorkspaceEntity;
import io.yak.ops.dao.mapper.governance.GovernanceRuleResultMapper;
import io.yak.ops.dao.mapper.governance.GovernanceScanRunMapper;
import io.yak.ops.dao.mapper.workspace.WorkspaceMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Submits runs and reads them back; the execution itself belongs to the scan executor. */
@Service
public class GovernanceScanServiceImpl implements GovernanceScanService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceScanServiceImpl.class);

    /** Actor a scheduled run is submitted under. */
    private static final String SYSTEM_ACTOR = "system";

    private final GovernanceScanRunMapper scanRunMapper;

    private final GovernanceRuleResultMapper ruleResultMapper;

    private final GovernanceRuleRegistry registry;

    private final GovernanceScanWriter writer;

    private final GovernanceScanDispatcher dispatcher;

    private final GovernanceScanExecutor executor;

    private final WorkspaceMapper workspaceMapper;

    private final GovernanceAssembler assembler;

    public GovernanceScanServiceImpl(
            GovernanceScanRunMapper scanRunMapper,
            GovernanceRuleResultMapper ruleResultMapper,
            GovernanceRuleRegistry registry,
            GovernanceScanWriter writer,
            GovernanceScanDispatcher dispatcher,
            GovernanceScanExecutor executor,
            WorkspaceMapper workspaceMapper,
            GovernanceAssembler assembler) {
        this.scanRunMapper = scanRunMapper;
        this.ruleResultMapper = ruleResultMapper;
        this.registry = registry;
        this.writer = writer;
        this.dispatcher = dispatcher;
        this.executor = executor;
        this.workspaceMapper = workspaceMapper;
        this.assembler = assembler;
    }

    @Override
    public GovernanceScanVO submit(String workspaceId, String actor) {
        return request(workspaceId, actor, GovernanceScanTrigger.MANUAL, true);
    }

    @Override
    public void submitScheduledForAllWorkspaces() {
        List<String> workspaceIds = workspaceMapper.selectList(null).stream()
                .map(WorkspaceEntity::getId)
                .toList();
        for (String workspaceId : workspaceIds) {
            try {
                request(workspaceId, SYSTEM_ACTOR, GovernanceScanTrigger.SCHEDULED, false);
            } catch (RuntimeException failure) {
                log.warn("scheduled governance scan for workspace {} was not submitted", workspaceId, failure);
            }
        }
    }

    @Override
    public PageData<GovernanceScanVO> page(String workspaceId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        List<GovernanceScanVO> rows = scanRunMapper.page(workspaceId, offset, pageSize).stream()
                .map(assembler::toScan)
                .toList();
        return PageData.of(rows, scanRunMapper.countAll(workspaceId), pageNo, pageSize);
    }

    @Override
    public GovernanceScanDetailVO detail(String workspaceId, String scanId) {
        GovernanceScanRunEntity run = scanRunMapper.findById(workspaceId, scanId);
        if (run == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        GovernanceScanDetailVO detail = new GovernanceScanDetailVO();
        detail.setScan(assembler.toScan(run));
        detail.setRules(ruleResultMapper.listByScan(workspaceId, scanId).stream()
                .map(assembler::toRuleResult)
                .toList());
        return detail;
    }

    @Override
    public GovernanceScanVO latest(String workspaceId) {
        List<GovernanceScanRunEntity> recent = scanRunMapper.listRecent(workspaceId, 1);
        return recent.isEmpty() ? null : assembler.toScan(recent.get(0));
    }

    /**
     * Inserts a pending run and hands it to the pool.
     *
     * <p>The insert commits before the hand-off, so the worker always sees the row it claims. A run
     * that cannot be handed off is released immediately: leaving it pending would hold the unique
     * active key and turn every later submission into {@code SCAN_BUSY}.
     */
    private GovernanceScanVO request(
            String workspaceId, String actor, GovernanceScanTrigger trigger, boolean failWhenBusy) {
        if (scanRunMapper.findActive(workspaceId) != null || dispatcher.remainingCapacity() <= 0) {
            if (failWhenBusy) {
                throw new BusinessException(GovernanceErrorCode.SCAN_BUSY);
            }
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        GovernanceScanRunEntity row = new GovernanceScanRunEntity();
        row.setId(IdUtils.nextId());
        row.setWorkspaceId(workspaceId);
        row.setTriggerType(trigger.getValue());
        row.setStatus("PENDING");
        row.setActiveScope(1);
        row.setRuleCount(registry.rules().size());
        row.setSucceededRuleCount(0);
        row.setFailedRuleCount(0);
        row.setIssueCreatedCount(0);
        row.setIssueReopenedCount(0);
        row.setIssueResolvedCount(0);
        row.setCreateTime(now);
        row.setCreateBy(actor);
        try {
            writer.createRun(row);
        } catch (DuplicateKeyException concurrent) {
            if (failWhenBusy) {
                throw new BusinessException(GovernanceErrorCode.SCAN_BUSY);
            }
            return null;
        }
        if (!dispatcher.dispatch(workspaceId, row.getId())) {
            executor.abandon(workspaceId, row.getId());
            if (failWhenBusy) {
                throw new BusinessException(GovernanceErrorCode.SCAN_BUSY);
            }
            return null;
        }
        return assembler.toScan(row);
    }
}
