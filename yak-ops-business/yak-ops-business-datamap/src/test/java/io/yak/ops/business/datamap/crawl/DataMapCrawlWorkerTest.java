package io.yak.ops.business.datamap.crawl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.config.DataMapProperties;
import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.dao.entity.datamap.DataMapColumnEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapTableEntity;
import io.yak.ops.dao.mapper.datamap.DataMapColumnMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableChangeMapper;
import io.yak.ops.dao.mapper.datamap.DataMapTableMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.PlatformTransactionManager;

/** Verifies claim ownership, the enumeration ceiling and per-table failure semantics. */
class DataMapCrawlWorkerTest {

    @Mock
    private DataMapCrawlMapper crawlMapper;

    @Mock
    private DataMapTableMapper tableMapper;

    @Mock
    private DataMapColumnMapper columnMapper;

    @Mock
    private DataMapTableChangeMapper changeMapper;

    @Mock
    private DataSourceCatalogService catalogService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private DataMapProperties properties;

    private DataMapCrawlWorker worker;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        properties = new DataMapProperties();
        worker = new DataMapCrawlWorker(
                crawlMapper, tableMapper, columnMapper, changeMapper, catalogService, transactionManager, properties);
        when(crawlMapper.findById("c1")).thenReturn(run());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void aLostClaimDoesNoWork() {
        when(crawlMapper.claim(eq("c1"), any())).thenReturn(0);
        worker.run("c1");
        verifyNoInteractions(catalogService);
        verify(crawlMapper, never())
                .finishSucceeded(any(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void theCeilingFailsTheRunBeforeAnyColumnIsRead() {
        when(crawlMapper.claim(eq("c1"), any())).thenReturn(1);
        List<DataSourceCatalogTableVO> tables = Collections.nCopies(2_000, catalogTable("orders"));
        when(catalogService.listAllTables("ds", "demo", null)).thenReturn(tables);
        worker.run("c1");
        verify(crawlMapper).finishFailed(
                eq("c1"),
                eq(DataMapErrorCode.CRAWL_TOO_LARGE.getMessage()),
                eq(2_000),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                any());
        verify(catalogService, never()).listColumns(any(), any());
    }

    @Test
    void aPerTableFailureDoesNotMarkAnythingMissing() {
        when(crawlMapper.claim(eq("c1"), any())).thenReturn(1);
        when(catalogService.listAllTables("ds", "demo", null)).thenReturn(List.of(catalogTable("orders")));
        when(catalogService.listColumns(eq("ds"), any())).thenThrow(new IllegalStateException("denied"));
        worker.run("c1");
        verify(crawlMapper).insertFailure(any());
        verify(crawlMapper).finishSucceeded(eq("c1"), eq(1), eq(0), eq(0), eq(0), eq(0), eq(1), any());
        verify(tableMapper, never()).markMissing(any(), any(), any(), any(), any(), any());
    }

    @Test
    void changeDetectionReadsTheBaselineBeforeReplacingTheStoredColumns() {
        when(crawlMapper.claim(eq("c1"), any())).thenReturn(1);
        when(catalogService.listAllTables("ds", "demo", null)).thenReturn(List.of(catalogTable("orders")));
        when(tableMapper.findByIdentity("ws", "ds", "demo", "", "orders")).thenReturn(existing());
        when(catalogService.listColumns(eq("ds"), any()))
                .thenReturn(List.of(catalogColumn("id", "VARCHAR", 100)));
        when(columnMapper.listByTable("ws", "t1")).thenReturn(List.of(storedColumn("id", "VARCHAR", 50)));

        worker.run("c1");

        InOrder order = inOrder(columnMapper, changeMapper);
        order.verify(columnMapper).listByTable("ws", "t1");
        order.verify(changeMapper).insert(any());
        order.verify(columnMapper).deleteByTable("ws", "t1");
        order.verify(columnMapper).insert(any());
    }

    private static DataMapCrawlEntity run() {
        DataMapCrawlEntity row = new DataMapCrawlEntity();
        row.setId("c1");
        row.setWorkspaceId("ws");
        row.setDatasourceId("ds");
        row.setDatabaseName("demo");
        row.setSchemaName("");
        row.setStatus("PENDING");
        row.setCreateBy("user");
        return row;
    }

    private static DataMapTableEntity existing() {
        DataMapTableEntity row = new DataMapTableEntity();
        row.setId("t1");
        row.setWorkspaceId("ws");
        row.setDatasourceId("ds");
        row.setDatabaseName("demo");
        row.setSchemaName("");
        row.setName("orders");
        row.setStatus("ACTIVE");
        return row;
    }

    private static DataSourceCatalogColumnVO catalogColumn(String name, String typeName, Integer size) {
        DataSourceCatalogColumnVO column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(12);
        column.setSize(size);
        column.setNullable(true);
        column.setPrimaryKey(false);
        column.setOrdinalPosition(1);
        return column;
    }

    private static DataMapColumnEntity storedColumn(String name, String typeName, Integer size) {
        DataMapColumnEntity column = new DataMapColumnEntity();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(12);
        column.setSize(size);
        column.setNullable(true);
        column.setPrimaryKey(false);
        column.setOrdinal(1);
        return column;
    }

    private static DataSourceCatalogTableVO catalogTable(String name) {
        DataSourceCatalogTableVO table = new DataSourceCatalogTableVO();
        table.setDatabase("demo");
        table.setName(name);
        table.setType("TABLE");
        return table;
    }
}
