package io.yak.ops.business.datamap.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.comment.DataMapCommentService.CommentThread;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.datamap.DataMapCommentEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCommentMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verifies the comment thread service rules.
 *
 * <p>The load-bearing rules are the ones a comment thread cannot recover from once broken: a reply
 * must never attach across assets, deletion must stay author-only, and the reply counter must stay
 * consistent with the rows the foreign-key cascade leaves behind.
 */
class DataMapCommentServiceImplTest {

    private static final String WORKSPACE = "ws-1";
    private static final String TABLE_ID = "tbl-1";
    private static final String TARGET_TYPE = "DATA_MAP_TABLE";
    private static final String ACTOR = "user-1";
    private static final String ACTOR_NAME = "Alice";
    private static final String OTHER = "user-2";

    @Mock
    private DataMapCommentMapper commentMapper;

    @Mock
    private DataMapTableMapper tableMapper;

    @InjectMocks
    private DataMapCommentServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(tableMapper.findById(WORKSPACE, TABLE_ID)).thenReturn(new DataMapTableEntity());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private DataMapCommentEntity topLevel(String id) {
        DataMapCommentEntity row = new DataMapCommentEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setTargetType(TARGET_TYPE);
        row.setTargetId(TABLE_ID);
        row.setParentId(null);
        row.setAuthorId(ACTOR);
        return row;
    }

    @Test
    void createTrimsBodyAndFillsAuditFields() {
        DataMapCommentEntity saved = service.create(
                WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "  hello  ", null);

        assertEquals(ACTOR, saved.getAuthorId());
        assertEquals(ACTOR_NAME, saved.getAuthorName());
        assertEquals("hello", saved.getBody());
        assertEquals(0, saved.getReplyCount());
        assertEquals(ACTOR, saved.getCreateBy());
        assertTrue(saved.getId().matches("\\d+"), "comment ids come from the unified snowflake id");
        verify(commentMapper).insert(saved);
        verify(commentMapper, never()).incrementReplyCount(anyString());
    }

    @Test
    void createRejectsBlankBody() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "   ", null));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void createRejectsBodyOver2000Chars() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "x".repeat(2001), null));
        assertEquals(DataMapErrorCode.COMMENT_TOO_LONG, error.getErrorCode());
    }

    @Test
    void createRejectsUnsupportedTargetType() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, "SOME_OTHER_KIND", TABLE_ID, "hi", null));
        assertEquals(DataMapErrorCode.COMMENT_TARGET_INVALID, error.getErrorCode());
    }

    @Test
    void createRejectsMissingTargetAsset() {
        when(tableMapper.findById(WORKSPACE, "ghost")).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, "ghost", "hi", null));
        assertEquals(DataMapErrorCode.COMMENT_TARGET_INVALID, error.getErrorCode());
    }

    @Test
    void createReplyIncrementsParentCounter() {
        DataMapCommentEntity parent = topLevel("p1");
        when(commentMapper.findById(WORKSPACE, "p1")).thenReturn(parent);

        service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "a reply", "p1");

        ArgumentCaptor<DataMapCommentEntity> captor = ArgumentCaptor.forClass(DataMapCommentEntity.class);
        verify(commentMapper).insert(captor.capture());
        assertEquals("p1", captor.getValue().getParentId());
        verify(commentMapper).incrementReplyCount("p1");
    }

    @Test
    void createReplyRejectsMissingParent() {
        when(commentMapper.findById(WORKSPACE, "p1")).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "a reply", "p1"));
        assertEquals(DataMapErrorCode.COMMENT_PARENT_MISMATCH, error.getErrorCode());
        verify(commentMapper, never()).insert(any());
    }

    @Test
    void createReplyRejectsNestedParent() {
        DataMapCommentEntity reply = topLevel("r1");
        reply.setParentId("p1");
        when(commentMapper.findById(WORKSPACE, "r1")).thenReturn(reply);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "a reply", "r1"));
        assertEquals(DataMapErrorCode.COMMENT_PARENT_MISMATCH, error.getErrorCode());
    }

    @Test
    void createReplyRejectsParentOnAnotherAsset() {
        DataMapCommentEntity parent = topLevel("p1");
        parent.setTargetId("tbl-2");
        when(commentMapper.findById(WORKSPACE, "p1")).thenReturn(parent);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(WORKSPACE, ACTOR, ACTOR_NAME, TARGET_TYPE, TABLE_ID, "a reply", "p1"));
        assertEquals(DataMapErrorCode.COMMENT_PARENT_MISMATCH, error.getErrorCode());
    }

    @Test
    void deleteRejectsMissingComment() {
        when(commentMapper.findById(WORKSPACE, "c1")).thenReturn(null);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(WORKSPACE, ACTOR, "c1"));
        assertEquals(DataMapErrorCode.COMMENT_NOT_FOUND, error.getErrorCode());
        verify(commentMapper, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteRejectsNonAuthor() {
        when(commentMapper.findById(WORKSPACE, "c1")).thenReturn(topLevel("c1"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(WORKSPACE, OTHER, "c1"));
        assertEquals(DataMapErrorCode.COMMENT_FORBIDDEN, error.getErrorCode());
        verify(commentMapper, never()).delete(anyString(), anyString());
    }

    @Test
    void deleteTopLevelLeavesParentCounterAlone() {
        when(commentMapper.findById(WORKSPACE, "c1")).thenReturn(topLevel("c1"));
        service.delete(WORKSPACE, ACTOR, "c1");
        verify(commentMapper).delete(WORKSPACE, "c1");
        verify(commentMapper, never()).decrementReplyCount(anyString());
    }

    @Test
    void deleteReplyDecrementsParentCounter() {
        DataMapCommentEntity reply = topLevel("r1");
        reply.setParentId("p1");
        when(commentMapper.findById(WORKSPACE, "r1")).thenReturn(reply);
        service.delete(WORKSPACE, ACTOR, "r1");
        verify(commentMapper).delete(WORKSPACE, "r1");
        verify(commentMapper).decrementReplyCount("p1");
    }

    @Test
    void pageNestsRepliesUnderTheirParent() {
        DataMapCommentEntity newer = topLevel("c2");
        DataMapCommentEntity older = topLevel("c1");
        DataMapCommentEntity r1 = topLevel("r1");
        r1.setParentId("c1");
        DataMapCommentEntity r2 = topLevel("r2");
        r2.setParentId("c1");
        when(commentMapper.countTopLevel(WORKSPACE, TARGET_TYPE, TABLE_ID)).thenReturn(2L);
        when(commentMapper.pageTopLevel(WORKSPACE, TARGET_TYPE, TABLE_ID, 20, 0))
                .thenReturn(List.of(newer, older));
        when(commentMapper.repliesOfTarget(WORKSPACE, TARGET_TYPE, TABLE_ID)).thenReturn(List.of(r1, r2));

        PageData<CommentThread> page = service.page(WORKSPACE, TARGET_TYPE, TABLE_ID, ACTOR, 1, 20);

        assertEquals(2, page.records().size());
        assertEquals("c2", page.records().get(0).comment().getId());
        assertEquals(List.of(), page.records().get(0).replies());
        assertEquals(List.of("r1", "r2"),
                page.records().get(1).replies().stream().map(DataMapCommentEntity::getId).toList());
    }

    @Test
    void pageSkipsQueriesWhenThreadEmpty() {
        when(commentMapper.countTopLevel(WORKSPACE, TARGET_TYPE, TABLE_ID)).thenReturn(0L);
        PageData<CommentThread> page = service.page(WORKSPACE, TARGET_TYPE, TABLE_ID, ACTOR, 1, 20);
        assertEquals(0, page.total());
        verify(commentMapper, never()).pageTopLevel(anyString(), anyString(), anyString(), eq(20), eq(0));
    }

    @Test
    void unreadCountDelegatesToMapper() {
        when(commentMapper.unreadCount(WORKSPACE, TARGET_TYPE, TABLE_ID, ACTOR)).thenReturn(7L);
        assertEquals(7L, service.unreadCount(WORKSPACE, TARGET_TYPE, TABLE_ID, ACTOR));
    }

    @Test
    void advanceReadUpsertsMarkerForCaller() {
        service.advanceRead(WORKSPACE, ACTOR, TABLE_ID);
        verify(commentMapper).upsertReadMarker(
                any(), eq(WORKSPACE), eq(TABLE_ID), eq(ACTOR), any());
    }
}
