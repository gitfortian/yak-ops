package io.yak.ops.business.modeling.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.DataSourceCatalogService;
import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.publication.impl.ModelPublicationServiceImpl;
import io.yak.ops.common.bean.dto.modeling.PublicationTargetDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogColumnVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceCatalogTableVO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.ModelFieldVO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.PublicationStatus;
import io.yak.ops.common.enums.modeling.StoragePolicy;
import io.yak.ops.common.enums.modeling.publication.PublicationErrorCode;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.LayerTargetEntity;
import io.yak.ops.dao.entity.modeling.ModelPublicationEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.LayerTargetRepository;
import io.yak.ops.dao.repository.modeling.PublicationRepository;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Publication flow: environment resolution, schema diff, dangerous-operation handling and the state machine. */
class ModelPublicationServiceTest {

    private static final String WORKSPACE = "ws1";
    private static final String ACTOR = "u1";
    private static final String MODEL = "m1";
    private static final PublicationTargetDTO TARGET = new PublicationTargetDTO("DEVELOP", null);

    @Mock ModelService models;
    @Mock LayerRepository layers;
    @Mock LayerTargetRepository layerTargets;
    @Mock DataSourceLookupService resources;
    @Mock DataSourceCatalogService catalog;
    @Mock PhysicalExecutor executor;
    @Mock PublicationRepository publications;
    @Mock MetadataCollector collector;
    @InjectMocks ModelPublicationServiceImpl service;

    AutoCloseable mocks;
    private ModelPublicationEntity stored;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(models.detail(eq(WORKSPACE), eq(ACTOR), eq(MODEL))).thenReturn(model());
        var layer = new LayerEntity();
        layer.setId("l1");
        layer.setCnName("明细层");
        layer.setEnabled(true);
        when(layers.layer(WORKSPACE, "l1")).thenReturn(Optional.of(layer));
        when(layerTargets.target(WORKSPACE, "l1", "DEVELOP")).thenReturn(Optional.of(target()));
        when(resources.dataSource(WORKSPACE, "ds1")).thenReturn(new DataSourceInfo("MYSQL", "warehouse", null, 7L));
        when(catalog.listDatabases("ds1")).thenReturn(List.of("warehouse"));
        when(catalog.listTables(eq("ds1"), any())).thenReturn(List.of());
        when(executor.execute(eq(WORKSPACE), eq("ds1"), anyList())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
                    stored = invocation.getArgument(0);
                    return null;
                })
                .when(publications)
                .add(any());
        when(publications.find(eq(WORKSPACE), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored));
        when(publications.finish(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (stored == null) return false;
                    stored.setStatus(invocation.getArgument(2));
                    stored.setError(invocation.getArgument(3));
                    stored.setDurationMs(invocation.getArgument(4));
                    stored.setExecutionLog(invocation.getArgument(5));
                    return true;
                });
        when(collector.collect(anyString(), anyString(), any()))
                .thenReturn(new MetadataSnapshotVO("c1", "p1", "SUCCEEDED", 1, 1, null, now(), now(), List.of()));
    }

    private static LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 24, 12, 0);
    }

    private static LayerTargetEntity target() {
        var row = new LayerTargetEntity();
        row.setLayerId("l1");
        row.setEnvironment("DEVELOP");
        row.setDataSourceId("ds1");
        row.setPhysicalDatabase("warehouse");
        return row;
    }

    private static ModelFieldVO field(String code, String cnName) {
        return new ModelFieldVO(
                code, 1, code, cnName, DataType.STRING, "32", true, true, false, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static ModelFieldVO bigintField(String code, String cnName) {
        return new ModelFieldVO(
                code, 2, code, cnName, DataType.BIGINT, null, false, true, false, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static ModelVO model() {
        return model(null);
    }

    private static ModelVO model(String physicalProperties) {
        return new ModelVO(
                MODEL,
                ModelType.DWD,
                "dwd_order",
                "订单明细",
                "l1",
                "明细",
                "c1",
                "交易",
                "p1",
                "下单",
                "d1",
                "交易域",
                null,
                null,
                null,
                null,
                StoragePolicy.DAILY_INCREMENTAL,
                30,
                ACTOR,
                null,
                physicalProperties,
                null,
                1,
                1,
                List.of(field("order_no", "订单号"), bigintField("tenant_id", "租户")),
                null);
    }

    private static DataSourceCatalogTableVO observedTable() {
        var table = new DataSourceCatalogTableVO();
        table.setDatabase("warehouse");
        table.setName("dwd_order");
        table.setType("TABLE");
        return table;
    }

    private static DataSourceCatalogColumnVO observedColumn(String name, String typeName, int size) {
        var column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(12);
        column.setSize(size);
        column.setNullable(false);
        column.setPrimaryKey(true);
        column.setPrimaryKeyPosition(1);
        column.setRemarks("订单号");
        return column;
    }

    private static DataSourceCatalogColumnVO observedNonKeyColumn(String name, String typeName, int size) {
        var column = new DataSourceCatalogColumnVO();
        column.setName(name);
        column.setTypeName(typeName);
        column.setJdbcType(-5);
        column.setSize(size);
        column.setNullable(false);
        column.setPrimaryKey(false);
        column.setRemarks("租户");
        return column;
    }

    @Test
    void createsTheTableWhenTheTargetIsAbsent() {
        var result = service.publish(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals(PublicationStatus.SUCCEEDED, result.status());
        var statements = ArgumentCaptor.forClass(List.class);
        verify(executor).execute(eq(WORKSPACE), eq("ds1"), statements.capture());
        assertEquals(1, statements.getValue().size());
        assertTrue(result.ddl().contains("CREATE TABLE `warehouse`.`dwd_order`"));
        assertEquals("CREATE", result.operation());
        assertEquals("DEVELOP", result.environment().name());
        assertTrue(result.durationMs() >= 0);
        assertTrue(result.executionLog().contains("发布成功"), result.executionLog());
        verify(collector).collect(eq(WORKSPACE), eq(ACTOR), any());
    }

    @Test
    void answersNoneWhenTheExistingTableMatchesAndExecutesNothing() {
        when(catalog.listTables(eq("ds1"), any())).thenReturn(List.of(observedTable()));
        when(catalog.listColumns(eq("ds1"), any()))
                .thenReturn(List.of(observedColumn("order_no", "VARCHAR", 32), observedColumn("tenant_id", "BIGINT", 19)));

        var failure = assertThrows(BusinessException.class, () -> service.publish(WORKSPACE, ACTOR, MODEL, TARGET));

        assertEquals(PublicationErrorCode.INVALID_PUBLICATION_STATE, failure.getErrorCode());
        verify(executor, never()).execute(anyString(), anyString(), any());
        verifyNoInteractions(collector);

        var preview = service.preview(WORKSPACE, ACTOR, MODEL, TARGET);
        assertEquals("NONE", preview.operation());
        assertNull(preview.ddl());
        assertTrue(preview.changes().isEmpty());
    }

    @Test
    void altersAnExistingTableWhoseColumnsDrift() {
        when(catalog.listTables(eq("ds1"), any())).thenReturn(List.of(observedTable()));
        when(catalog.listColumns(eq("ds1"), any())).thenReturn(List.of(observedColumn("order_no", "VARCHAR", 64)));

        var preview = service.preview(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals("ALTER", preview.operation());
        assertEquals(2, preview.changes().size());
        assertEquals("CHANGE", preview.changes().get(0).kind());
        assertEquals("order_no", preview.changes().get(0).column());
        assertEquals("ADD", preview.changes().get(1).kind());
        assertTrue(preview.ddl().contains("ADD COLUMN `tenant_id` bigint"), preview.ddl());
        assertTrue(preview.ddl().contains("MODIFY COLUMN `order_no` varchar(32)"), preview.ddl());
        verify(executor, never()).execute(anyString(), anyString(), any());

        when(executor.execute(eq(WORKSPACE), eq("ds1"), anyList())).thenReturn(List.of());
        var result = service.publish(WORKSPACE, ACTOR, MODEL, TARGET);
        assertEquals(PublicationStatus.SUCCEEDED, result.status());
        assertEquals("ALTER", result.operation());
    }

    @Test
    void refusesToPublishADropWithoutTheLossAcknowledgement() {
        var record = stored();
        when(catalog.listTables(eq("ds1"), any())).thenReturn(List.of(observedTable()));
        // The physical table carries a column the model no longer declares: dropping it loses data.
        when(catalog.listColumns(eq("ds1"), any()))
                .thenReturn(List.of(
                        observedColumn("order_no", "VARCHAR", 32), observedColumn("tenant_id", "BIGINT", 19),
                        observedColumn("legacy", "VARCHAR", 8)));

        var failure = assertThrows(BusinessException.class, () -> service.publish(WORKSPACE, ACTOR, MODEL, TARGET));

        assertEquals(PublicationErrorCode.DANGEROUS_OPERATION_UNCONFIRMED, failure.getErrorCode());
        verify(executor, never()).execute(anyString(), anyString(), any());
        verify(publications, never()).add(any());

        var result = service.publish(WORKSPACE, ACTOR, MODEL, new PublicationTargetDTO("DEVELOP", true));

        assertEquals(PublicationStatus.SUCCEEDED, result.status());
        assertTrue(result.ddl().contains("DROP COLUMN `legacy`"), result.ddl());
    }

    @Test
    void resolvesTheTargetFromTheLayerBindingInsteadOfTheRequest() {
        var failure = assertThrows(
                BusinessException.class,
                () -> service.publish(WORKSPACE, ACTOR, MODEL, new PublicationTargetDTO("PROD", null)));

        assertEquals(PublicationErrorCode.TARGET_NOT_BOUND, failure.getErrorCode());
        verifyNoInteractions(executor);
    }

    @Test
    void refusesEnginePropertiesTheDialectCannotExpress() {
        when(models.detail(eq(WORKSPACE), eq(ACTOR), eq(MODEL))).thenReturn(model("{\"bucketCount\":10}"));

        var failure = assertThrows(BusinessException.class, () -> service.publish(WORKSPACE, ACTOR, MODEL, TARGET));

        assertTrue(failure.getMessage().contains("不支持物理表属性"), failure.getMessage());
    }

    @Test
    void keepsThePublicationWhenOnlyTheSnapshotFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("catalog down"))
                .when(collector)
                .collect(anyString(), anyString(), any());

        var result = service.publish(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals(PublicationStatus.SUCCEEDED, result.status());
        verify(collector).recordFailure(eq(WORKSPACE), eq(ACTOR), anyString(), anyString());
    }

    @Test
    void recordsUnknownWhenTheConnectionBreaksMidStatement() {
        when(executor.execute(eq(WORKSPACE), eq("ds1"), anyList()))
                .thenThrow(new PhysicalExecutionException(new SQLException("gone", "08006")));

        var result = service.publish(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals(PublicationStatus.UNKNOWN, result.status());
        assertTrue(result.executionLog().contains("发布失败"), result.executionLog());
        verifyNoInteractions(collector);
    }

    @Test
    void recordsFailureWhenTheEngineRefusesTheStatement() {
        when(executor.execute(eq(WORKSPACE), eq("ds1"), anyList()))
                .thenThrow(new PhysicalExecutionException(new SQLException("bad ddl", "42000")));

        var result = service.publish(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals(PublicationStatus.FAILED, result.status());
        assertTrue(result.error().contains("SQLException"), result.error());
        assertTrue(result.executionLog().contains("SQLException"), result.executionLog());
    }

    @Test
    void previewsWithoutRecordingAnything() {
        var preview = service.preview(WORKSPACE, ACTOR, MODEL, TARGET);

        assertEquals("warehouse", preview.database());
        assertEquals("dwd_order", preview.table());
        assertTrue(preview.ddl().contains("CREATE TABLE"));
        verify(publications, never()).add(any());
        verifyNoInteractions(executor);
    }

    @Test
    void refreshesOnlyASuccessfulPublication() {
        var record = new ModelPublicationEntity();
        record.setId("p1");
        record.setStatus(PublicationStatus.FAILED.name());
        when(publications.find(WORKSPACE, "p1")).thenReturn(Optional.of(record));

        assertThrows(BusinessException.class, () -> service.refreshMetadata(WORKSPACE, ACTOR, "p1"));
        verify(collector, never()).collect(anyString(), anyString(), any());
    }

    @Test
    void reconcilesAnUnknownPublicationWithoutReexecutingDdl() {
        var record = stored();
        record.setStatus(PublicationStatus.UNKNOWN.name());
        when(catalog.listTables(eq("ds1"), any())).thenReturn(List.of(observedTable()));
        when(catalog.listColumns(eq("ds1"), any()))
                .thenReturn(List.of(observedColumn("order_no", "VARCHAR", 32), observedNonKeyColumn("tenant_id", "BIGINT", 19)));
        doAnswer(invocation -> {
            record.setStatus(invocation.getArgument(2));
            return true;
        })
                .when(publications)
                .reconcile(eq(WORKSPACE), eq("p1"), anyString(), any(), any());

        var result = service.reconcile(WORKSPACE, ACTOR, "p1");

        assertEquals(PublicationStatus.SUCCEEDED, result.status());
        verify(executor, never()).execute(anyString(), anyString(), any());
    }

    @Test
    void treatsAnAbsentSnapshotAsAMissingResource() {
        var record = new ModelPublicationEntity();
        record.setId("p1");
        when(publications.find(WORKSPACE, "p1")).thenReturn(Optional.of(record));
        // A collection that ran and failed still writes a row, so empty means it never ran.
        when(collector.snapshot(WORKSPACE, "p1")).thenReturn(Optional.empty());

        var failure = assertThrows(BusinessException.class, () -> service.metadata(WORKSPACE, "p1"));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, failure.getErrorCode());
    }

    @Test
    void refusesToReconcileATerminalPublication() {
        var record = new ModelPublicationEntity();
        record.setId("p1");
        record.setStatus(PublicationStatus.SUCCEEDED.name());
        when(publications.find(WORKSPACE, "p1")).thenReturn(Optional.of(record));

        assertThrows(BusinessException.class, () -> service.reconcile(WORKSPACE, ACTOR, "p1"));
    }

    /** A freshly recorded PENDING row the publish flow can finish. */
    private ModelPublicationEntity stored() {
        var record = new ModelPublicationEntity();
        record.setId("p1");
        record.setWorkspaceId(WORKSPACE);
        record.setDatasourceId("ds1");
        record.setModelId(MODEL);
        record.setPhysicalDatabase("warehouse");
        record.setPhysicalTable("dwd_order");
        record.setStatus(PublicationStatus.PENDING.name());
        when(publications.find(WORKSPACE, "p1")).thenReturn(Optional.of(record));
        doAnswer(invocation -> {
                    record.setStatus(invocation.getArgument(2));
                    return true;
                })
                .when(publications)
                .finish(eq(WORKSPACE), eq("p1"), anyString(), any(), any(), any(), any());
        return record;
    }
}
