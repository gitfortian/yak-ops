package io.yak.ops.business.datamap.tagging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.tagging.impl.TagServiceImpl;
import io.yak.ops.common.bean.dto.tag.TagBindingBatchDTO;
import io.yak.ops.common.bean.dto.tag.TagKeyRenameDTO;
import io.yak.ops.common.bean.dto.tag.TagKeySaveDTO;
import io.yak.ops.common.bean.dto.tag.TagValueSaveDTO;
import io.yak.ops.common.bean.vo.tag.TagKeyVO;
import io.yak.ops.common.bean.vo.tag.TagValueVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.tag.TagBindingOperation;
import io.yak.ops.common.enums.tag.TagErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.entity.tag.TagKeyEntity;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.entity.tag.TableTagValueEntity;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.tag.TagMapper;
import io.yak.ops.dao.mapper.tag.TagValueUsageRow;
import io.yak.ops.workspace.WorkspaceService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** Verifies the dictionary rules: the manager gate, uniqueness, limits and delete protection. */
class TagServiceTest {

    private static final String WORKSPACE = "ws";
    private static final String MANAGER = "manager";
    private static final String MEMBER = "member";

    @Mock
    private TagMapper tagMapper;

    @Mock
    private DataMapTableMapper tableMapper;

    @Mock
    private WorkspaceService workspaceService;

    @InjectMocks
    private TagServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void aMemberCannotWriteTheDictionary() {
        when(workspaceService.isManager(WORKSPACE, MEMBER)).thenReturn(false);

        assertTagForbidden(() -> service.createKey(WORKSPACE, MEMBER, keySave("dept")));
        assertTagForbidden(() -> service.renameKey(WORKSPACE, MEMBER, "key-1", rename("dept")));
        assertTagForbidden(() -> service.deleteKey(WORKSPACE, MEMBER, "key-1"));
        assertTagForbidden(() -> service.addValue(WORKSPACE, MEMBER, "key-1", valueSave("电商")));
        assertTagForbidden(() -> service.renameValue(WORKSPACE, MEMBER, "value-1", valueSave("电商")));
        assertTagForbidden(() -> service.deleteValue(WORKSPACE, MEMBER, "value-1"));

        verify(tagMapper, never()).insertKey(any());
        verify(tagMapper, never()).deleteKey(any(), any());
        verify(tagMapper, never()).deleteValue(any(), any());
    }

    @Test
    void aManagerCreatesAKeyWithItsInitialValues() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(null);
        List<TagKeyEntity> insertedKeys = new ArrayList<>();
        when(tagMapper.insertKey(any())).thenAnswer(invocation -> {
            insertedKeys.add(invocation.getArgument(0));
            return 1;
        });
        List<TagValueEntity> insertedValues = new ArrayList<>();
        when(tagMapper.insertValue(any())).thenAnswer(invocation -> {
            insertedValues.add(invocation.getArgument(0));
            return 1;
        });
        when(tagMapper.findKeyById(eq(WORKSPACE), any())).thenAnswer(invocation -> insertedKeys.get(0));
        when(tagMapper.listValues(WORKSPACE)).thenAnswer(invocation -> insertedValues);

        TagKeyVO created = service.createKey(WORKSPACE, MANAGER, keySave("dept", "电商", "零售"));

        assertEquals("dept", created.getName());
        assertEquals(2, created.getValues().size());
        assertTrue(insertedValues.stream().allMatch(row -> MANAGER.equals(row.getCreateBy())));
        assertTrue(insertedValues.stream().allMatch(row -> insertedKeys.get(0).getId().equals(row.getKeyId())));
    }

    @Test
    void aDuplicateKeyNameIsRejected() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(key("other", "dept"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.createKey(WORKSPACE, MANAGER, keySave("dept")));

        assertEquals(TagErrorCode.DUPLICATE_TAG_KEY, failure.getErrorCode());
    }

    @Test
    void aDuplicateValueNameInsideOneRequestIsRejected() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(null);

        BusinessException failure = assertThrows(
                BusinessException.class, () -> service.createKey(WORKSPACE, MANAGER, keySave("dept", "电商", "Dept-电商", "电商")));

        assertEquals(TagErrorCode.DUPLICATE_TAG_VALUE, failure.getErrorCode());
    }

    @Test
    void aKeyLosingTheNameRaceReportsTheDuplicateCode() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(null);
        when(tagMapper.insertKey(any())).thenThrow(new DuplicateKeyException("uk_tag_key_name"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.createKey(WORKSPACE, MANAGER, keySave("dept")));

        assertEquals(TagErrorCode.DUPLICATE_TAG_KEY, failure.getErrorCode());
    }

    @Test
    void renamingOntoAnotherKeyNameIsRejected() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "key-1")).thenReturn(key("key-1", "dept"));
        when(tagMapper.findKeyByName(WORKSPACE, "subject")).thenReturn(key("key-2", "subject"));

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.renameKey(WORKSPACE, MANAGER, "key-1", rename("subject")));

        assertEquals(TagErrorCode.DUPLICATE_TAG_KEY, failure.getErrorCode());
    }

    @Test
    void renamingToTheSameNameIsAllowed() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "key-1")).thenReturn(key("key-1", "dept"));
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(key("key-1", "dept"));
        when(tagMapper.listValues(WORKSPACE)).thenReturn(List.of());

        TagKeyVO renamed = service.renameKey(WORKSPACE, MANAGER, "key-1", rename("dept"));

        assertEquals("dept", renamed.getName());
        verify(tagMapper).renameKey(eq(WORKSPACE), eq("key-1"), eq("dept"), any(), eq(MANAGER));
    }

    @Test
    void aBoundKeyCannotBeDeleted() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "key-1")).thenReturn(key("key-1", "dept"));
        when(tagMapper.deleteKey(WORKSPACE, "key-1")).thenThrow(new DataIntegrityViolationException("fk"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.deleteKey(WORKSPACE, MANAGER, "key-1"));

        assertEquals(TagErrorCode.TAG_IN_USE, failure.getErrorCode());
    }

    @Test
    void anUnboundKeyDeletesAndCascadesItsValues() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "key-1")).thenReturn(key("key-1", "dept"));
        when(tagMapper.deleteKey(WORKSPACE, "key-1")).thenReturn(1);

        service.deleteKey(WORKSPACE, MANAGER, "key-1");

        verify(tagMapper).deleteKey(WORKSPACE, "key-1");
    }

    @Test
    void aKeyStopsAcceptingValuesAtTwoHundred() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "key-1")).thenReturn(key("key-1", "dept"));
        when(tagMapper.findValueByName("key-1", "new")).thenReturn(null);
        when(tagMapper.countValuesByKey(WORKSPACE, "key-1")).thenReturn(200L);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.addValue(WORKSPACE, MANAGER, "key-1", valueSave("new")));

        assertEquals(TagErrorCode.TOO_MANY_TAG_VALUES_PER_KEY, failure.getErrorCode());
        verify(tagMapper, never()).insertValue(any());
    }

    @Test
    void aKeyWithTooManyInitialValuesIsRejected() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyByName(WORKSPACE, "dept")).thenReturn(null);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 201; index++) values.add("value-" + index);

        BusinessException failure = assertThrows(
                BusinessException.class, () -> service.createKey(WORKSPACE, MANAGER, keySave("dept", values)));

        assertEquals(TagErrorCode.TOO_MANY_TAG_VALUES_PER_KEY, failure.getErrorCode());
        verify(tagMapper, never()).insertKey(any());
    }

    @Test
    void aValueOfAUnknownKeyIsRejected() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findKeyById(WORKSPACE, "missing")).thenReturn(null);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.addValue(WORKSPACE, MANAGER, "missing", valueSave("电商")));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
    }

    @Test
    void aBoundValueCannotBeDeleted() {
        when(workspaceService.isManager(WORKSPACE, MANAGER)).thenReturn(true);
        when(tagMapper.findValueById(WORKSPACE, "value-1")).thenReturn(value("value-1", "key-1", "电商"));
        when(tagMapper.deleteValue(WORKSPACE, "value-1")).thenThrow(new DataIntegrityViolationException("fk"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.deleteValue(WORKSPACE, MANAGER, "value-1"));

        assertEquals(TagErrorCode.TAG_IN_USE, failure.getErrorCode());
    }

    @Test
    void aReplaceSetAboveTwentyValuesIsRejected() {
        List<String> requested = new ArrayList<>();
        for (int index = 0; index < 21; index++) requested.add("value-" + index);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.replaceTableValues(WORKSPACE, MEMBER, "table-1", requested));

        assertEquals(TagErrorCode.TOO_MANY_TAGS_PER_ASSET, failure.getErrorCode());
        verify(tableMapper, never()).lockById(any(), any());
    }

    @Test
    void aReplaceSetLocksTheAssetBeforeRewriting() {
        List<String> requested = List.of("value-1", "value-2");
        when(tableMapper.lockById(WORKSPACE, "table-1")).thenReturn("table-1");
        when(tagMapper.findValuesByIds(WORKSPACE, requested))
                .thenReturn(List.of(value("value-1", "key-1", "电商"), value("value-2", "key-1", "零售")));

        service.replaceTableValues(WORKSPACE, MEMBER, "table-1", requested);

        verify(tableMapper).lockById(WORKSPACE, "table-1");
        verify(tagMapper).deleteBindingsByTable(WORKSPACE, "table-1");
        ArgumentCaptor<List<TableTagValueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagMapper).insertBindings(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(row -> MEMBER.equals(row.getCreateBy())));
    }

    @Test
    void aReplaceSetOnAnUnknownAssetIsRejected() {
        when(tableMapper.lockById(WORKSPACE, "missing")).thenReturn(null);

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.replaceTableValues(WORKSPACE, MEMBER, "missing", List.of("value-1")));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
        verify(tagMapper, never()).deleteBindingsByTable(any(), any());
    }

    @Test
    void aReplaceSetWithAnUnknownValueIsRejected() {
        when(tableMapper.lockById(WORKSPACE, "table-1")).thenReturn("table-1");
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("value-1", "missing"))).thenReturn(List.of());

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.replaceTableValues(WORKSPACE, MEMBER, "table-1", List.of("value-1", "missing")));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
        verify(tagMapper, never()).deleteBindingsByTable(any(), any());
    }

    @Test
    void aBatchAboveTheCeilingsIsRejected() {
        List<String> tables = new ArrayList<>();
        for (int index = 0; index < 101; index++) tables.add("table-" + index);
        TagBindingBatchDTO input = batch(tables, List.of("value-1"));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.applyBatch(WORKSPACE, MEMBER, input));

        assertEquals(TagErrorCode.BATCH_TOO_LARGE, failure.getErrorCode());
    }

    @Test
    void aBatchWhoseProductExceedsTheBindingCeilingIsRejected() {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < 20; index++) values.add("value-" + index);
        // 51 tables times 20 values is one thousand and twenty pairs, past the one-thousand ceiling.
        List<String> manyTables = new ArrayList<>();
        for (int index = 0; index < 51; index++) manyTables.add("table-" + index);
        TagBindingBatchDTO input = batch(manyTables, values);

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.applyBatch(WORKSPACE, MEMBER, input));

        assertEquals(TagErrorCode.BATCH_TOO_LARGE, failure.getErrorCode());
        verify(tagMapper, never()).insertBindings(anyList());
    }

    @Test
    void aBatchNamesTheForeignTable() {
        TagBindingBatchDTO input = batch(List.of("table-1", "table-foreign"), List.of("value-1"));
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("value-1")))
                .thenReturn(List.of(value("value-1", "key-1", "电商")));
        when(tableMapper.findByIds(WORKSPACE, List.of("table-1", "table-foreign")))
                .thenReturn(List.of(tableRow("table-1")));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.applyBatch(WORKSPACE, MEMBER, input));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
        assertEquals("table-foreign", failure.getDetail());
        verify(tagMapper, never()).insertBindings(anyList());
    }

    @Test
    void aBatchAddRejectsATableThatWouldExceedTwentyValues() {
        TagBindingBatchDTO input = batch(List.of("table-1"), List.of("value-1", "value-2"));
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("value-1", "value-2")))
                .thenReturn(List.of(value("value-1", "key-1", "电商"), value("value-2", "key-1", "零售")));
        when(tableMapper.findByIds(WORKSPACE, List.of("table-1"))).thenReturn(List.of(tableRow("table-1")));
        when(tagMapper.listBindingsByTablesAndValues(WORKSPACE, List.of("table-1"), List.of("value-1", "value-2")))
                .thenReturn(List.of());
        List<TableTagValueEntity> existing = new ArrayList<>();
        for (int index = 0; index < 19; index++) existing.add(binding("table-1", "old-" + index));
        when(tagMapper.listBindings(WORKSPACE, List.of("table-1"))).thenReturn(existing);

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.applyBatch(WORKSPACE, MEMBER, input));

        assertEquals(TagErrorCode.TOO_MANY_TAGS_PER_ASSET, failure.getErrorCode());
        assertEquals("table-1", failure.getDetail());
        verify(tagMapper, never()).insertBindings(anyList());
    }

    @Test
    void aBatchAddSkipsPairsThatAreAlreadyBound() {
        TagBindingBatchDTO input = batch(List.of("table-1"), List.of("value-1", "value-2"), TagBindingOperation.ADD);
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("value-1", "value-2")))
                .thenReturn(List.of(value("value-1", "key-1", "电商"), value("value-2", "key-1", "零售")));
        when(tableMapper.findByIds(WORKSPACE, List.of("table-1")))
                .thenReturn(List.of(tableRow("table-1")));
        when(tagMapper.listBindingsByTablesAndValues(WORKSPACE, List.of("table-1"), List.of("value-1", "value-2")))
                .thenReturn(List.of(binding("table-1", "value-1")));
        when(tagMapper.listBindings(WORKSPACE, List.of("table-1"))).thenReturn(List.of(binding("table-1", "value-1")));
        when(tagMapper.insertBindings(anyList())).thenReturn(1);

        int affected = service.applyBatch(WORKSPACE, MEMBER, input);

        assertEquals(1, affected);
        ArgumentCaptor<List<TableTagValueEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(tagMapper).insertBindings(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("value-2", captor.getValue().get(0).getTagValueId());
    }

    @Test
    void aBatchRemoveDeletesTheSelectedPairs() {
        TagBindingBatchDTO input = batch(List.of("table-1"), List.of("value-1"), TagBindingOperation.REMOVE);
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("value-1")))
                .thenReturn(List.of(value("value-1", "key-1", "电商")));
        when(tableMapper.findByIds(WORKSPACE, List.of("table-1"))).thenReturn(List.of(tableRow("table-1")));
        when(tagMapper.listBindingsByTablesAndValues(WORKSPACE, List.of("table-1"), List.of("value-1")))
                .thenReturn(List.of(binding("table-1", "value-1")));
        when(tagMapper.deleteBindingsByTablesAndValues(WORKSPACE, List.of("table-1"), List.of("value-1")))
                .thenReturn(1);

        int affected = service.applyBatch(WORKSPACE, MEMBER, input);

        assertEquals(1, affected);
        verify(tagMapper, never()).insertBindings(anyList());
    }

    @Test
    void theDictionaryListsValuesWithTheirUsageCounts() {
        when(tagMapper.listKeys(WORKSPACE)).thenReturn(List.of(key("key-1", "dept")));
        when(tagMapper.listValues(WORKSPACE)).thenReturn(List.of(value("value-1", "key-1", "电商")));
        TagValueUsageRow usage = new TagValueUsageRow();
        usage.setValueId("value-1");
        usage.setUsageCount(3L);
        when(tagMapper.listUsageCounts(WORKSPACE)).thenReturn(List.of(usage));

        List<TagKeyVO> keys = service.listKeys(WORKSPACE);

        assertEquals(1, keys.size());
        TagValueVO value = keys.get(0).getValues().get(0);
        assertEquals("电商", value.getName());
        assertEquals(3L, value.getUsageCount());
    }

    private static void assertTagForbidden(Runnable command) {
        BusinessException failure = assertThrows(BusinessException.class, command::run);
        assertEquals(TagErrorCode.TAG_WRITE_FORBIDDEN, failure.getErrorCode());
    }

    private static TagKeySaveDTO keySave(String name, String... values) {
        TagKeySaveDTO input = new TagKeySaveDTO();
        input.setName(name);
        input.setValues(List.of(values));
        return input;
    }

    private static TagKeySaveDTO keySave(String name, List<String> values) {
        TagKeySaveDTO input = new TagKeySaveDTO();
        input.setName(name);
        input.setValues(values);
        return input;
    }

    private static TagKeyRenameDTO rename(String name) {
        TagKeyRenameDTO input = new TagKeyRenameDTO();
        input.setName(name);
        return input;
    }

    private static TagValueSaveDTO valueSave(String name) {
        TagValueSaveDTO input = new TagValueSaveDTO();
        input.setName(name);
        return input;
    }

    private static TagBindingBatchDTO batch(List<String> tableIds, List<String> valueIds) {
        return batch(tableIds, valueIds, TagBindingOperation.ADD);
    }

    private static TagBindingBatchDTO batch(
            List<String> tableIds, List<String> valueIds, TagBindingOperation operation) {
        TagBindingBatchDTO input = new TagBindingBatchDTO();
        input.setTableIds(tableIds);
        input.setTagValueIds(valueIds);
        input.setOperation(operation);
        return input;
    }

    private static TagKeyEntity key(String id, String name) {
        TagKeyEntity row = new TagKeyEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setName(name);
        return row;
    }

    private static TagValueEntity value(String id, String keyId, String name) {
        TagValueEntity row = new TagValueEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setKeyId(keyId);
        row.setName(name);
        return row;
    }

    private static TableTagValueEntity binding(String tableId, String valueId) {
        TableTagValueEntity row = new TableTagValueEntity();
        row.setWorkspaceId(WORKSPACE);
        row.setTableId(tableId);
        row.setTagValueId(valueId);
        return row;
    }

    private static DataMapTableEntity tableRow(String id) {
        DataMapTableEntity row = new DataMapTableEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        return row;
    }
}
