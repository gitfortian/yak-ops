package io.yak.ops.business.governance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.governance.rule.GovernanceRule;
import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.service.GovernanceIssueService;
import io.yak.ops.business.governance.support.GovernanceAssembler;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueBatchDTO;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueQueryDTO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueDetailVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueEventVO;
import io.yak.ops.common.bean.vo.governance.GovernanceIssueVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.governance.GovernanceErrorCode;
import io.yak.ops.common.enums.governance.GovernanceEventType;
import io.yak.ops.common.enums.governance.IssueStatus;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.IdUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.governance.GovernanceIssueEntity;
import io.yak.ops.dao.entity.governance.GovernanceIssueEventEntity;
import io.yak.ops.dao.entity.workspace.WorkspaceMemberEntity;
import io.yak.ops.dao.mapper.governance.GovernanceIssueEventMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssueMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssuePageQuery;
import io.yak.ops.dao.mapper.workspace.WorkspaceMemberMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual state machine of a governance Issue.
 *
 * <p>Every command locks its rows with {@code SELECT ... FOR UPDATE} and validates the state it read
 * before writing, so a concurrent scan and a manual action serialise on the Issue row instead of
 * racing. A batch validates every row first and writes afterwards: one invalid Issue rejects the
 * whole command, which is the only honest outcome when the caller selected a set.
 */
@Service
public class GovernanceIssueServiceImpl implements GovernanceIssueService {

    /** Largest batch the endpoint accepts; mirrors the request validation. */
    private static final int MAX_BATCH = 200;

    /** Longest ignore window; a longer one is a policy decision this release does not make. */
    private static final int IGNORE_LIMIT_DAYS = 90;

    /** Events returned by the detail surface. */
    private static final int DETAIL_EVENT_LIMIT = 50;

    private final GovernanceIssueMapper issueMapper;

    private final GovernanceIssueEventMapper eventMapper;

    private final WorkspaceMemberMapper memberMapper;

    private final GovernanceRuleRegistry registry;

    private final GovernanceAssembler assembler;

    public GovernanceIssueServiceImpl(
            GovernanceIssueMapper issueMapper,
            GovernanceIssueEventMapper eventMapper,
            WorkspaceMemberMapper memberMapper,
            GovernanceRuleRegistry registry,
            GovernanceAssembler assembler) {
        this.issueMapper = issueMapper;
        this.eventMapper = eventMapper;
        this.memberMapper = memberMapper;
        this.registry = registry;
        this.assembler = assembler;
    }

    @Override
    public PageData<GovernanceIssueVO> page(String workspaceId, GovernanceIssueQueryDTO query) {
        GovernanceIssueQueryDTO filter = query == null ? new GovernanceIssueQueryDTO() : query;
        int pageNo = filter.getPageNo() == null ? 1 : filter.getPageNo();
        int pageSize = filter.getPageSize() == null ? 20 : filter.getPageSize();
        GovernanceIssuePageQuery page = new GovernanceIssuePageQuery();
        page.setStatus(StringUtils.trimToNull(filter.getStatus()));
        page.setSeverity(StringUtils.trimToNull(filter.getSeverity()));
        page.setDimension(StringUtils.trimToNull(filter.getDimension()));
        page.setRuleCode(StringUtils.trimToNull(filter.getRuleCode()));
        page.setOwnerId(StringUtils.trimToNull(filter.getOwnerId()));
        page.setTargetType(StringUtils.trimToNull(filter.getTargetType()));
        page.setTargetId(StringUtils.trimToNull(filter.getTargetId()));
        page.setKeyword(StringUtils.trimToNull(filter.getKeyword()));
        page.setOffset((pageNo - 1) * pageSize);
        page.setLimit(pageSize);
        List<GovernanceIssueVO> rows = issueMapper.page(workspaceId, page).stream()
                .map(assembler::toIssue)
                .toList();
        return PageData.of(rows, issueMapper.count(workspaceId, page), pageNo, pageSize);
    }

    @Override
    public GovernanceIssueDetailVO detail(String workspaceId, String issueId) {
        GovernanceIssueEntity issue = requireIssue(workspaceId, issueId);
        GovernanceIssueDetailVO detail = new GovernanceIssueDetailVO();
        detail.setIssue(assembler.toIssue(issue));
        Optional<GovernanceRule> rule = registry.find(issue.getRuleCode());
        detail.setRule(rule.map(assembler::toRule).orElse(null));
        detail.setEvents(eventMapper.pageByIssue(workspaceId, issueId, 0, DETAIL_EVENT_LIMIT).stream()
                .map(assembler::toEvent)
                .toList());
        return detail;
    }

    @Override
    public PageData<GovernanceIssueEventVO> events(String workspaceId, String issueId, int pageNo, int pageSize) {
        requireIssue(workspaceId, issueId);
        List<GovernanceIssueEventVO> rows =
                eventMapper.pageByIssue(workspaceId, issueId, (pageNo - 1) * pageSize, pageSize).stream()
                        .map(assembler::toEvent)
                        .toList();
        return PageData.of(rows, rows.size(), pageNo, pageSize);
    }

    @Override
    @Transactional
    public int assign(String workspaceId, String actor, GovernanceIssueBatchDTO input) {
        List<GovernanceIssueEntity> issues = lockAll(workspaceId, input.getIssueIds());
        String ownerId = StringUtils.trimToNull(input.getOwnerId());
        if (ownerId == null || !isActiveMember(workspaceId, ownerId)) {
            throw new BusinessException(GovernanceErrorCode.INVALID_OWNER);
        }
        for (GovernanceIssueEntity issue : issues) {
            if (!status(issue).isAssignable()) {
                throw new BusinessException(GovernanceErrorCode.INVALID_ISSUE_STATE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String note = StringUtils.trimToNull(input.getNote());
        for (GovernanceIssueEntity issue : issues) {
            issueMapper.assign(workspaceId, issue.getId(), ownerId, now, actor);
            appendEvent(
                    workspaceId,
                    issue.getId(),
                    GovernanceEventType.ASSIGNED,
                    issue.getStatus(),
                    IssueStatus.HANDLING.getValue(),
                    ownerId,
                    null,
                    note,
                    actor,
                    now);
        }
        return issues.size();
    }

    @Override
    @Transactional
    public int resolve(String workspaceId, String actor, GovernanceIssueBatchDTO input) {
        List<GovernanceIssueEntity> issues = lockAll(workspaceId, input.getIssueIds());
        for (GovernanceIssueEntity issue : issues) {
            if (status(issue) == IssueStatus.RESOLVED) {
                throw new BusinessException(GovernanceErrorCode.INVALID_ISSUE_STATE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String note = StringUtils.trimToNull(input.getNote());
        for (GovernanceIssueEntity issue : issues) {
            issueMapper.resolve(workspaceId, issue.getId(), now, actor);
            appendEvent(
                    workspaceId,
                    issue.getId(),
                    GovernanceEventType.RESOLVED,
                    issue.getStatus(),
                    IssueStatus.RESOLVED.getValue(),
                    issue.getOwnerId(),
                    null,
                    note,
                    actor,
                    now);
        }
        return issues.size();
    }

    @Override
    @Transactional
    public int reopen(String workspaceId, String actor, GovernanceIssueBatchDTO input) {
        List<GovernanceIssueEntity> issues = lockAll(workspaceId, input.getIssueIds());
        for (GovernanceIssueEntity issue : issues) {
            if (status(issue) != IssueStatus.RESOLVED) {
                throw new BusinessException(GovernanceErrorCode.INVALID_ISSUE_STATE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String note = StringUtils.trimToNull(input.getNote());
        for (GovernanceIssueEntity issue : issues) {
            issueMapper.reopen(workspaceId, issue.getId(), now, actor);
            appendEvent(
                    workspaceId,
                    issue.getId(),
                    GovernanceEventType.REOPENED,
                    issue.getStatus(),
                    IssueStatus.OPEN.getValue(),
                    issue.getOwnerId(),
                    null,
                    note,
                    actor,
                    now);
        }
        return issues.size();
    }

    @Override
    @Transactional
    public int ignore(String workspaceId, String actor, GovernanceIssueBatchDTO input) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = input.getIgnoreUntil();
        if (until == null || !until.isAfter(now) || until.isAfter(now.plusDays(IGNORE_LIMIT_DAYS))) {
            throw new BusinessException(GovernanceErrorCode.INVALID_IGNORE_WINDOW);
        }
        List<GovernanceIssueEntity> issues = lockAll(workspaceId, input.getIssueIds());
        for (GovernanceIssueEntity issue : issues) {
            IssueStatus current = status(issue);
            if (current != IssueStatus.OPEN && current != IssueStatus.HANDLING && current != IssueStatus.IGNORED) {
                throw new BusinessException(GovernanceErrorCode.INVALID_ISSUE_STATE);
            }
        }
        String note = StringUtils.trimToNull(input.getNote());
        int changed = 0;
        for (GovernanceIssueEntity issue : issues) {
            if (status(issue) == IssueStatus.IGNORED && until.equals(issue.getIgnoreUntil())) {
                continue;
            }
            issueMapper.ignore(workspaceId, issue.getId(), until, now, actor);
            appendEvent(
                    workspaceId,
                    issue.getId(),
                    GovernanceEventType.IGNORED,
                    issue.getStatus(),
                    IssueStatus.IGNORED.getValue(),
                    issue.getOwnerId(),
                    until,
                    note,
                    actor,
                    now);
            changed++;
        }
        return changed;
    }

    @Override
    @Transactional
    public int unignore(String workspaceId, String actor, GovernanceIssueBatchDTO input) {
        List<GovernanceIssueEntity> issues = lockAll(workspaceId, input.getIssueIds());
        for (GovernanceIssueEntity issue : issues) {
            if (status(issue) != IssueStatus.IGNORED) {
                throw new BusinessException(GovernanceErrorCode.INVALID_ISSUE_STATE);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String note = StringUtils.trimToNull(input.getNote());
        for (GovernanceIssueEntity issue : issues) {
            issueMapper.unignore(workspaceId, issue.getId(), now, actor);
            appendEvent(
                    workspaceId,
                    issue.getId(),
                    GovernanceEventType.UNIGNORED,
                    issue.getStatus(),
                    IssueStatus.OPEN.getValue(),
                    issue.getOwnerId(),
                    null,
                    note,
                    actor,
                    now);
        }
        return issues.size();
    }

    private GovernanceIssueEntity requireIssue(String workspaceId, String issueId) {
        GovernanceIssueEntity issue = issueMapper.findById(workspaceId, issueId);
        if (issue == null) {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        }
        return issue;
    }

    /**
     * Locks every requested Issue in identity order.
     *
     * <p>Sorting removes the deadlock two overlapping batches would otherwise create. A missing row
     * rejects the command instead of silently shrinking the batch the caller asked for.
     */
    private List<GovernanceIssueEntity> lockAll(String workspaceId, List<String> issueIds) {
        if (issueIds == null || issueIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(issueIds);
        if (unique.size() > MAX_BATCH) {
            throw new BusinessException(GovernanceErrorCode.BATCH_TOO_LARGE);
        }
        List<GovernanceIssueEntity> issues = new ArrayList<>(unique.size());
        for (String issueId : unique.stream().sorted().toList()) {
            GovernanceIssueEntity issue = issueMapper.lockById(workspaceId, issueId);
            if (issue == null) {
                throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
            }
            issues.add(issue);
        }
        return issues;
    }

    private boolean isActiveMember(String workspaceId, String userId) {
        Long count = memberMapper.selectCount(Wrappers.<WorkspaceMemberEntity>lambdaQuery()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)
                .eq(WorkspaceMemberEntity::getUserId, userId));
        return count != null && count > 0;
    }

    private static IssueStatus status(GovernanceIssueEntity issue) {
        return IssueStatus.valueOf(issue.getStatus());
    }

    private void appendEvent(
            String workspaceId,
            String issueId,
            GovernanceEventType type,
            String fromStatus,
            String toStatus,
            String ownerId,
            LocalDateTime ignoreUntil,
            String note,
            String actorId,
            LocalDateTime now) {
        GovernanceIssueEventEntity event = new GovernanceIssueEventEntity();
        event.setId(IdUtils.nextId());
        event.setWorkspaceId(workspaceId);
        event.setIssueId(issueId);
        event.setEventType(type.getValue());
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOwnerId(ownerId);
        event.setIgnoreUntil(ignoreUntil);
        event.setNote(note);
        event.setActorId(actorId);
        event.setCreateTime(now);
        eventMapper.insert(event);
    }
}
