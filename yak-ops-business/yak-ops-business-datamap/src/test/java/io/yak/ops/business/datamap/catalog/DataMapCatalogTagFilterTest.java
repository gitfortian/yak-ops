package io.yak.ops.business.datamap.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.catalog.impl.DataMapCatalogServiceImpl;
import io.yak.ops.business.datamap.support.DataMapSearchRateLimiter;
import io.yak.ops.business.modeling.planning.PlanningService;
import io.yak.ops.common.bean.dto.datamap.DataMapTableQueryDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.tag.TagValueEntity;
import io.yak.ops.dao.mapper.datamap.DataMapPersonalMapper;
import io.yak.ops.dao.mapper.datamap.DataMapSearchCriteria;
import io.yak.ops.dao.mapper.datamap.DataMapTableChangeMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import io.yak.ops.dao.mapper.tag.TagMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Verifies the catalog tag filter groups requested values by their key before building SQL. */
class DataMapCatalogTagFilterTest {

    private static final String WORKSPACE = "ws";

    @Mock
    private DataMapTableMapper tableMapper;

    @Mock
    private DataMapTableChangeMapper changeMapper;

    @Mock
    private DataMapPersonalMapper personalMapper;

    @Mock
    private DataMapAssetAssembler assembler;

    @Mock
    private DataMapSearchRateLimiter rateLimiter;

    @Mock
    private PlanningService planningService;

    @Mock
    private TagMapper tagMapper;

    @InjectMocks
    private DataMapCatalogServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @Test
    void requestedValuesAreGroupedByTheirKey() {
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("v1", "v2", "v3")))
                .thenReturn(List.of(value("v1", "k1"), value("v2", "k1"), value("v3", "k2")));
        when(tableMapper.count(any())).thenReturn(0L);
        when(assembler.summaries(any(), any(), any())).thenReturn(List.of());

        service.search(WORKSPACE, "actor", query(List.of("v1", "v2", "v3")));

        ArgumentCaptor<DataMapSearchCriteria> captor = ArgumentCaptor.forClass(DataMapSearchCriteria.class);
        org.mockito.Mockito.verify(tableMapper).count(captor.capture());
        assertEquals(List.of(List.of("v1", "v2"), List.of("v3")), captor.getValue().getTagValueGroups());
    }

    @Test
    void duplicateRequestedValuesCollapseIntoOneGroup() {
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("v1"))).thenReturn(List.of(value("v1", "k1")));
        when(tableMapper.count(any())).thenReturn(0L);
        when(assembler.summaries(any(), any(), any())).thenReturn(List.of());

        service.search(WORKSPACE, "actor", query(List.of("v1", "v1")));

        ArgumentCaptor<DataMapSearchCriteria> captor = ArgumentCaptor.forClass(DataMapSearchCriteria.class);
        org.mockito.Mockito.verify(tableMapper).count(captor.capture());
        assertEquals(List.of(List.of("v1")), captor.getValue().getTagValueGroups());
    }

    @Test
    void anUnknownValueIdRejectsTheFilterInsteadOfDroppingIt() {
        when(tagMapper.findValuesByIds(WORKSPACE, List.of("v1", "missing"))).thenReturn(List.of(value("v1", "k1")));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.search(WORKSPACE, "actor", query(List.of("v1", "missing"))));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
        org.mockito.Mockito.verify(tableMapper, org.mockito.Mockito.never()).count(any());
    }

    @Test
    void noTagFilterLeavesTheGroupsEmpty() {
        when(tableMapper.count(any())).thenReturn(0L);
        when(assembler.summaries(any(), any(), any())).thenReturn(List.of());

        service.search(WORKSPACE, "actor", query(null));

        ArgumentCaptor<DataMapSearchCriteria> captor = ArgumentCaptor.forClass(DataMapSearchCriteria.class);
        org.mockito.Mockito.verify(tableMapper).count(captor.capture());
        assertEquals(List.of(), captor.getValue().getTagValueGroups());
    }

    private static DataMapTableQueryDTO query(List<String> tagValueIds) {
        DataMapTableQueryDTO query = new DataMapTableQueryDTO();
        query.setTagValueIds(tagValueIds);
        query.setPageNo(1);
        query.setPageSize(20);
        return query;
    }

    private static TagValueEntity value(String id, String keyId) {
        TagValueEntity row = new TagValueEntity();
        row.setId(id);
        row.setWorkspaceId(WORKSPACE);
        row.setKeyId(keyId);
        row.setName(id);
        return row;
    }
}
