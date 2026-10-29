package io.yak.ops.business.governance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.governance.rule.GovernanceRuleRegistry;
import io.yak.ops.business.governance.rule.datamap.TableOwnerRule;
import io.yak.ops.business.governance.service.impl.GovernanceIssueServiceImpl;
import io.yak.ops.business.governance.support.GovernanceAssembler;
import io.yak.ops.common.bean.dto.governance.GovernanceIssueBatchDTO;
import io.yak.ops.common.enums.governance.GovernanceErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.governance.GovernanceIssueEntity;
import io.yak.ops.dao.mapper.governance.GovernanceIssueEventMapper;
import io.yak.ops.dao.mapper.governance.GovernanceIssueMapper;
import io.yak.ops.dao.mapper.workspace.WorkspaceMemberMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pins the manual state machine, including the whole-batch rejection rule. */
class GovernanceIssueServiceImplTest {

    private static final String WORKSPACE = "ws-1";

    private final GovernanceIssueMapper issueMapper = mock(GovernanceIssueMapper.class);

    private final GovernanceIssueEventMapper eventMapper = mock(GovernanceIssueEventMapper.class);

    private final WorkspaceMemberMapper memberMapper = mock(WorkspaceMemberMapper.class);

    private final GovernanceIssueServiceImpl service = new GovernanceIssueServiceImpl(
            issueMapper,
            eventMapper,
            memberMapper,
            new GovernanceRuleRegistry(List.of(new TableOwnerRule())),
            new GovernanceAssembler());

    private static GovernanceIssueEntity issue(String id, String status) {
        GovernanceIssueEntity row = new GovernanceIssueEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setRuleCode(TableOwnerRule.CODE);
        row.setStatus(status);
        row.setRevision(1L);
        return row;
    }

    private static GovernanceIssueBatchDTO batch(String... ids) {
        GovernanceIssueBatchDTO input = new GovernanceIssueBatchDTO();
        input.setIssueIds(List.of(ids));
        return input;
    }

    @Test
    void rejectsResolvingAnAlreadyResolvedIssue() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "RESOLVED"));

        BusinessException failure = assertThrows(
                BusinessException.class, () -> service.resolve(WORKSPACE, "u-1", batch("i-1")));

        assertEquals(GovernanceErrorCode.INVALID_ISSUE_STATE, failure.getErrorCode());
        verify(issueMapper, never()).resolve(anyString(), anyString(), any(), anyString());
    }

    @Test
    void aBatchWithOneInvalidRowWritesNothing() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "OPEN"));
        when(issueMapper.lockById(WORKSPACE, "i-2")).thenReturn(issue("i-2", "RESOLVED"));

        assertThrows(BusinessException.class, () -> service.resolve(WORKSPACE, "u-1", batch("i-1", "i-2")));

        verify(issueMapper, never()).resolve(anyString(), anyString(), any(), anyString());
        verify(eventMapper, never()).insert(any());
    }

    @Test
    void resolvesEveryRowOfAValidBatch() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "OPEN"));
        when(issueMapper.lockById(WORKSPACE, "i-2")).thenReturn(issue("i-2", "HANDLING"));

        assertEquals(2, service.resolve(WORKSPACE, "u-1", batch("i-1", "i-2")));

        verify(issueMapper).resolve(eq(WORKSPACE), eq("i-1"), any(LocalDateTime.class), eq("u-1"));
        verify(issueMapper).resolve(eq(WORKSPACE), eq("i-2"), any(LocalDateTime.class), eq("u-1"));
        verify(eventMapper, org.mockito.Mockito.times(2)).insert(any());
    }

    @Test
    void rejectsReopeningAnIssueThatIsNotResolved() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "OPEN"));

        assertThrows(BusinessException.class, () -> service.reopen(WORKSPACE, "u-1", batch("i-1")));
        verify(issueMapper, never()).reopen(anyString(), anyString(), any(), anyString());
    }

    @Test
    void rejectsAnIgnoreWindowInThePast() {
        GovernanceIssueBatchDTO input = batch("i-1");
        input.setIgnoreUntil(LocalDateTime.now().minusDays(1));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.ignore(WORKSPACE, "u-1", input));

        assertEquals(GovernanceErrorCode.INVALID_IGNORE_WINDOW, failure.getErrorCode());
        verify(issueMapper, never()).ignore(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void rejectsAnIgnoreWindowBeyondNinetyDays() {
        GovernanceIssueBatchDTO input = batch("i-1");
        input.setIgnoreUntil(LocalDateTime.now().plusDays(91));

        assertThrows(BusinessException.class, () -> service.ignore(WORKSPACE, "u-1", input));
    }

    @Test
    void ignoringToTheSameDeadlineAgainChangesNothing() {
        LocalDateTime until = LocalDateTime.now().plusDays(7).withNano(0);
        GovernanceIssueEntity ignored = issue("i-1", "IGNORED");
        ignored.setIgnoreUntil(until);
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(ignored);
        GovernanceIssueBatchDTO input = batch("i-1");
        input.setIgnoreUntil(until);

        assertEquals(0, service.ignore(WORKSPACE, "u-1", input));
        verify(issueMapper, never()).ignore(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void rejectsAnAssigneeOutsideTheWorkspace() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "OPEN"));
        when(memberMapper.selectCount(any())).thenReturn(0L);
        GovernanceIssueBatchDTO input = batch("i-1");
        input.setOwnerId("u-9");

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.assign(WORKSPACE, "u-1", input));

        assertEquals(GovernanceErrorCode.INVALID_OWNER, failure.getErrorCode());
        verify(issueMapper, never()).assign(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void assignsToAnActiveMember() {
        when(issueMapper.lockById(WORKSPACE, "i-1")).thenReturn(issue("i-1", "OPEN"));
        when(memberMapper.selectCount(any())).thenReturn(1L);
        GovernanceIssueBatchDTO input = batch("i-1");
        input.setOwnerId("u-9");

        assertEquals(1, service.assign(WORKSPACE, "u-1", input));
        verify(issueMapper).assign(eq(WORKSPACE), eq("i-1"), eq("u-9"), any(LocalDateTime.class), eq("u-1"));
        verify(eventMapper).insert(any());
    }

    @Test
    void rejectsAnUnknownIssue() {
        when(issueMapper.lockById(WORKSPACE, "missing")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.resolve(WORKSPACE, "u-1", batch("missing")));
    }
}
