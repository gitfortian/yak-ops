package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.LayerService;
import io.yak.ops.business.modeling.model.impl.ModelServiceImpl;
import io.yak.ops.common.bean.dto.modeling.ModelFieldInputDTO;
import io.yak.ops.common.bean.dto.modeling.ModelInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelDTO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.enums.modeling.StoragePolicy;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.DimensionEntity;
import io.yak.ops.dao.entity.modeling.ModelEntity;
import io.yak.ops.dao.entity.modeling.ModelFieldEntity;
import io.yak.ops.dao.entity.modeling.PlanningNodeEntity;
import io.yak.ops.dao.repository.modeling.DimensionRepository;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.PlanningRepository;
import io.yak.ops.dao.repository.modeling.StandardInheritance;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Model scope rules, table naming, field replacement and standard inheritance without a database. */
class ModelServiceTest {
    @Mock ModelRepository models;
    @Mock PlanningRepository planning;
    @Mock DimensionRepository dimensions;
    @Mock LayerService layers;
    @Mock WorkspaceService workspaces;
    @InjectMocks ModelServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(layers.options("ws", "actor")).thenReturn(List.of(new LayerOptionVO("layer", "DWD", "明细层", "ds", "主库")));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsInactiveActorsBeforeReadingLayers() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of())));

        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(models, layers);
    }

    @Test
    void refusesALayerThatIsDisabledOrUnbound() {
        when(layers.options("ws", "actor")).thenReturn(List.of());

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of())));

        assertEquals(DimensionModelingErrorCode.LAYER_NOT_SELECTABLE, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void prefixesTheTableNameWithTheSelectedLayer() {
        detailGraph();
        var stored = storedModel("dwd_order_detail");
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(ModelEntity.class);

        var saved = service.create("ws", "actor", detailInput("order_detail", List.of()));

        verify(models).add(captor.capture());
        assertEquals("dwd_order_detail", captor.getValue().getName());
        assertEquals("DWD", captor.getValue().getType());
        assertEquals("domain", captor.getValue().getDomainId());
        assertEquals("process", captor.getValue().getProcessId());
        assertEquals(ModelType.DWD, saved.type());
    }

    @Test
    void rejectsASuffixThatBreaksTheIdentifierContract() {
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("Order-Detail", List.of())));

        assertEquals(DimensionModelingErrorCode.INVALID_TABLE_NAME, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void rejectsATableNameLongerThanTheColumn() {
        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("a".repeat(61), List.of())));

        assertEquals(DimensionModelingErrorCode.INVALID_TABLE_NAME, error.getErrorCode());
    }

    @Test
    void requiresABusinessProcessForADetailTable() {
        categoryAndDomain();
        var input = detailInput("order_detail", List.of());
        input.setProcessId(null);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void refusesAProcessThatBelongsToAnotherDomain() {
        categoryAndDomain();
        node(NodeKind.PROCESS, "process", "other-domain");

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of())));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void refusesADataMartOnADetailTable() {
        detailGraph();
        var input = detailInput("order_detail", List.of());
        input.setDatamartId("mart");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void refusesADimensionTableWithoutExactlyOneScope() {
        node(NodeKind.CATEGORY, "cat", null);
        var input = dimensionTableInput("product", List.of(), null, null);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void refusesADimensionTableThatBindsBothScopes() {
        categoryAndDomain();
        node(NodeKind.DATASET, "mart", null);
        var input = dimensionTableInput("product", List.of(), "domain", "mart");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void requiresAPublicDimensionTableToBindADimensionOfItsDomain() {
        categoryAndDomain();
        dimensionInScope("dim", DimensionScope.PUBLIC, "domain");
        var input = dimensionTableInput("product", List.of(), "domain", null);
        input.setDimensionId(null);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void refusesADimensionBoundToAnotherScope() {
        categoryAndDomain();
        dimensionInScope("dim", DimensionScope.APPLICATION, null);
        var input = dimensionTableInput("product", List.of(), "domain", null);
        input.setDimensionId("dim");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void acceptsAnApplicationLayerDimensionTableWithoutADimension() {
        node(NodeKind.CATEGORY, "cat", null);
        node(NodeKind.DATASET, "mart", null);
        var stored = storedModel("dim_product");
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(ModelEntity.class);

        service.create("ws", "actor", dimensionTableInput("product", List.of(), null, "mart"));

        verify(models).add(captor.capture());
        // The prefix follows the selected layer, never the model type: a DIM on the DWD layer is dwd_*.
        assertEquals("dwd_product", captor.getValue().getName());
        assertEquals("mart", captor.getValue().getDatamartId());
        assertNull(captor.getValue().getDomainId());
        assertNull(captor.getValue().getDimensionId());
    }

    @Test
    void inheritsCodeTypeAndLengthFromTheBoundStandard() {
        detailGraph();
        when(models.standard("ws", "std"))
                .thenReturn(new StandardInheritance("order_id", DataType.BIGINT, "20"));
        var stored = storedModel("dwd_order_detail");
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var field = field("placeholder", DataType.STRING);
        field.setLengthPrecision("10");
        field.setStandardId("std");
        field.setIsNotNull(true);
        field.setIsPrimaryKey(true);
        var captor = ArgumentCaptor.forClass(ModelFieldEntity.class);

        service.create("ws", "actor", detailInput("order_detail", List.of(field)));

        verify(models).addField(captor.capture());
        var row = captor.getValue();
        assertEquals("order_id", row.getEnCode());
        assertEquals(DataType.BIGINT, row.getDataType());
        assertEquals("20", row.getLengthPrecision());
        assertTrue(Boolean.TRUE.equals(row.getIsPrimaryKey()));
        assertTrue(Boolean.TRUE.equals(row.getIsNotNull()));
        assertEquals("std", row.getStandardId());
    }

    @Test
    void keepsSubmittedNullabilityInsteadOfTheStandardContract() {
        detailGraph();
        when(models.standard("ws", "std"))
                .thenReturn(new StandardInheritance("order_id", DataType.BIGINT, null));
        var stored = storedModel("dwd_order_detail");
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var field = field("order_id", null);
        field.setStandardId("std");
        field.setIsNotNull(false);
        var captor = ArgumentCaptor.forClass(ModelFieldEntity.class);

        service.create("ws", "actor", detailInput("order_detail", List.of(field)));

        verify(models).addField(captor.capture());
        assertFalse(Boolean.TRUE.equals(captor.getValue().getIsNotNull()));
        assertFalse(Boolean.TRUE.equals(captor.getValue().getIsPrimaryKey()));
    }

    @Test
    void rejectsAFieldWithoutAType() {
        detailGraph();
        var field = field("order_id", null);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of(field))));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void rejectsDuplicateFieldCodesInsideOneModel() {
        detailGraph();
        var first = field("order_id", DataType.BIGINT);
        var second = field("order_id", DataType.STRING);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of(first, second))));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
    }

    @Test
    void requiresThePartitionFieldToBeAString() {
        detailGraph();
        var field = field("order_id", DataType.BIGINT);
        field.setIsPartition(true);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of(field))));

        assertEquals(DimensionModelingErrorCode.PARTITION_RULE, error.getErrorCode());
    }

    @Test
    void allowsOnlyOnePartitionField() {
        detailGraph();
        var first = field("ds", DataType.STRING);
        first.setIsPartition(true);
        var second = field("region", DataType.STRING);
        second.setIsPartition(true);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of(first, second))));

        assertEquals(DimensionModelingErrorCode.PARTITION_RULE, error.getErrorCode());
    }

    @Test
    void requiresABusinessProcessForASummaryTable() {
        categoryAndDomain();
        var input = summaryInput("order_summary",
                List.of(measure("amount", DataType.DECIMAL, AggregateType.SUM)));
        input.setProcessId(null);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void refusesADataMartOnASummaryTable() {
        detailGraph();
        var input = summaryInput("order_summary",
                List.of(measure("amount", DataType.DECIMAL, AggregateType.SUM)));
        input.setDatamartId("mart");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void requiresADataMartForAnApplicationTable() {
        node(NodeKind.CATEGORY, "cat", null);
        var input = applicationInput("order_report", List.of());
        input.setDatamartId(null);

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void refusesABusinessProcessOnAnApplicationTable() {
        node(NodeKind.CATEGORY, "cat", null);
        node(NodeKind.DATASET, "mart", null);
        var input = applicationInput("order_report", List.of());
        input.setProcessId("process");

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
    }

    @Test
    void requiresAtLeastOneMeasureOnASummaryTable() {
        detailGraph();
        var input = summaryInput("order_summary", List.of(field("order_id", DataType.BIGINT)));

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void refusesAMeasureOnADetailTable() {
        detailGraph();
        var input = detailInput("order_detail",
                List.of(measure("amount", DataType.DECIMAL, AggregateType.SUM)));

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
    }

    @Test
    void refusesSumOnAStringField() {
        detailGraph();
        var input = summaryInput("order_summary",
                List.of(measure("region", DataType.STRING, AggregateType.SUM)));

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
    }

    @Test
    void checksTheMeasureAgainstTheTypeInheritedFromTheStandard() {
        detailGraph();
        when(models.standard("ws", "std"))
                .thenReturn(new StandardInheritance("region", DataType.STRING, null));
        var bounded = measure("placeholder", DataType.DECIMAL, AggregateType.SUM);
        bounded.setStandardId("std");
        var input = summaryInput("order_summary", List.of(bounded));

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
    }

    @Test
    void acceptsCountOnAStringField() {
        detailGraph();
        var stored = storedModel("dws_order_summary");
        stored.setType(ModelType.DWS.name());
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(ModelFieldEntity.class);

        service.create("ws", "actor", summaryInput("order_summary",
                List.of(measure("region", DataType.STRING, AggregateType.COUNT))));

        verify(models).addField(captor.capture());
        assertEquals(AggregateType.COUNT, captor.getValue().getAggregateType());
    }

    @Test
    void acceptsAnApplicationTableWithoutAnyMeasure() {
        node(NodeKind.CATEGORY, "cat", null);
        node(NodeKind.DATASET, "mart", null);
        var stored = storedModel("ads_order_report");
        when(models.find(eq("ws"), anyString())).thenReturn(Optional.of(stored));
        when(models.fields(anyString())).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(ModelFieldEntity.class);

        service.create("ws", "actor", applicationInput("order_report",
                List.of(field("order_id", DataType.BIGINT))));

        verify(models).addField(captor.capture());
        assertNull(captor.getValue().getAggregateType());
    }

    @Test
    void rejectsATableNameThatAlreadyExists() {
        detailGraph();
        when(models.occupied("ws", "dwd_order_detail", null)).thenReturn(true);

        var error = assertThrows(BusinessException.class,
                () -> service.create("ws", "actor", detailInput("order_detail", List.of())));

        assertEquals(DimensionModelingErrorCode.DUPLICATE_TABLE_NAME, error.getErrorCode());
        verify(models, never()).add(any());
    }

    @Test
    void refusesToEditARowWithAnUnknownStoredType() {
        var row = storedModel("ods_order_snapshot");
        row.setType("ODS");
        when(models.lock("ws", row.getId())).thenReturn(Optional.of(row));
        var input = updateInput("summary", ModelType.DWD, 1);

        var error = assertThrows(BusinessException.class,
                () -> service.update("ws", "actor", row.getId(), input));

        assertEquals(DimensionModelingErrorCode.UNKNOWN_MODEL_TYPE, error.getErrorCode());
        verify(models, never()).update(any());
    }

    @Test
    void refusesToTurnADetailTableIntoADimensionTable() {
        var row = storedModel("dwd_order_detail");
        when(models.lock("ws", "m")).thenReturn(Optional.of(row));
        var input = updateInput("order_detail", ModelType.DIM, 1);

        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "m", input));

        assertEquals(DimensionModelingErrorCode.INVALID_MODEL, error.getErrorCode());
        verify(models, never()).update(any());
    }

    @Test
    void rejectsAStaleRevisionBeforeWriting() {
        var row = storedModel("dwd_order_detail");
        when(models.lock("ws", "m")).thenReturn(Optional.of(row));
        var input = updateInput("order_detail", ModelType.DWD, 9);

        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "m", input));

        assertEquals(DimensionModelingErrorCode.REVISION_CONFLICT, error.getErrorCode());
        verify(models, never()).update(any());
    }

    @Test
    void replacesTheWholeFieldListOnUpdate() {
        detailGraph();
        var row = storedModel("dwd_order_detail");
        row.setId("m");
        row.setRevision(3);
        when(models.lock("ws", "m")).thenReturn(Optional.of(row));
        when(models.find("ws", "m")).thenReturn(Optional.of(row));
        when(models.fields("m")).thenReturn(List.of());
        when(models.update(any())).thenReturn(true);
        var input = updateInput("order_detail", ModelType.DWD, 3);
        input.setFields(List.of(field("order_id", DataType.BIGINT), field("ds", DataType.STRING)));

        var saved = service.update("ws", "actor", "m", input);

        verify(models).deleteFields("m");
        verify(models, times(2)).addField(any());
        assertEquals(4, saved.revision());
    }

    @Test
    void refusesToDropTheLastMeasureOnEdit() {
        detailGraph();
        var row = storedModel("dws_order_summary");
        row.setId("m");
        row.setRevision(3);
        row.setType(ModelType.DWS.name());
        when(models.lock("ws", "m")).thenReturn(Optional.of(row));
        when(models.find("ws", "m")).thenReturn(Optional.of(row));
        when(models.fields("m")).thenReturn(List.of());
        var input = updateInput("order_summary", ModelType.DWS, 3);
        input.setFields(List.of(field("order_id", DataType.BIGINT)));

        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "m", input));

        assertEquals(DimensionModelingErrorCode.INVALID_FIELD, error.getErrorCode());
        // The whole-list replacement must not run, so the stored measure survives a refused edit.
        verify(models, never()).deleteFields("m");
        verify(models, never()).addField(any());
    }

    @Test
    void reportsNameAvailabilityWithoutReservingIt() {
        when(models.occupied("ws", "dwd_order_detail", null)).thenReturn(true);
        when(models.occupied("ws", "dwd_free_name", null)).thenReturn(false);

        assertFalse(service.nameAvailable("ws", "actor", "layer", "order_detail", null));
        assertTrue(service.nameAvailable("ws", "actor", "layer", "free_name", null));
    }

    @Test
    void reportsAMissingModelAsNotExisting() {
        when(models.find("ws", "missing")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.detail("ws", "actor", "missing"));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
    }

    private void detailGraph() {
        categoryAndDomain();
        node(NodeKind.PROCESS, "process", "domain");
    }

    private void categoryAndDomain() {
        node(NodeKind.CATEGORY, "cat", null);
        node(NodeKind.DOMAIN, "domain", "cat");
    }

    private void node(NodeKind kind, String id, String parentId) {
        var node = new PlanningNodeEntity();
        node.setId(id);
        node.setKind(kind);
        node.setParentId(parentId);
        node.setWorkspaceId("ws");
        when(planning.find("ws", kind, id)).thenReturn(Optional.of(node));
    }

    private void dimensionInScope(String id, DimensionScope scope, String scopeId) {
        var dimension = new DimensionEntity();
        dimension.setId(id);
        dimension.setWorkspaceId("ws");
        dimension.setScope(scope);
        if (scope == DimensionScope.PUBLIC) dimension.setDomainId(scopeId);
        else dimension.setDatamartId(scopeId);
        when(dimensions.find("ws", id)).thenReturn(Optional.of(dimension));
    }

    static ModelInputDTO detailInput(String suffix, List<ModelFieldInputDTO> fields) {
        var input = baseInput(suffix, fields);
        input.setType(ModelType.DWD);
        input.setProcessId("process");
        input.setDomainId("domain");
        return input;
    }

    static ModelInputDTO dimensionTableInput(
            String suffix, List<ModelFieldInputDTO> fields, String domainId, String datamartId) {
        var input = baseInput(suffix, fields);
        input.setType(ModelType.DIM);
        input.setDomainId(domainId);
        input.setDatamartId(datamartId);
        return input;
    }

    static ModelInputDTO summaryInput(String suffix, List<ModelFieldInputDTO> fields) {
        var input = baseInput(suffix, fields);
        input.setType(ModelType.DWS);
        input.setProcessId("process");
        input.setDomainId("domain");
        return input;
    }

    static ModelInputDTO applicationInput(String suffix, List<ModelFieldInputDTO> fields) {
        var input = baseInput(suffix, fields);
        input.setType(ModelType.ADS);
        input.setDatamartId("mart");
        return input;
    }

    static ModelInputDTO baseInput(String suffix, List<ModelFieldInputDTO> fields) {
        var input = new ModelInputDTO();
        input.setLayerId("layer");
        input.setCategoryId("cat");
        input.setNameSuffix(suffix);
        input.setCnName("订单明细");
        input.setStoragePolicy(StoragePolicy.DAILY_INCREMENTAL);
        input.setLifecycleDays(30);
        input.setFields(fields);
        return input;
    }

    static UpdateModelDTO updateInput(String suffix, ModelType type, long revision) {
        var input = new UpdateModelDTO();
        input.setType(type);
        input.setLayerId("layer");
        input.setCategoryId("cat");
        input.setNameSuffix(suffix);
        input.setCnName("订单明细");
        input.setStoragePolicy(StoragePolicy.DAILY_INCREMENTAL);
        input.setLifecycleDays(30);
        input.setRevision(revision);
        if (type.isPublicLayer()) {
            input.setDomainId("domain");
            input.setProcessId("process");
        } else if (type == ModelType.DIM) {
            input.setDomainId("domain");
        } else {
            input.setDatamartId("mart");
        }
        return input;
    }

    static ModelFieldInputDTO measure(String enCode, DataType dataType, AggregateType aggregate) {
        var field = field(enCode, dataType);
        field.setAggregateType(aggregate);
        return field;
    }

    static ModelFieldInputDTO field(String enCode, DataType dataType) {
        var field = new ModelFieldInputDTO();
        field.setEnCode(enCode);
        field.setCnName("字段");
        field.setDataType(dataType);
        return field;
    }

    static ModelEntity storedModel(String name) {
        var row = new ModelEntity();
        row.setId("stored-" + name);
        row.setWorkspaceId("ws");
        row.setType(ModelType.DWD.name());
        row.setName(name);
        row.setCnName("订单明细");
        row.setLayerId("layer");
        row.setCategoryId("cat");
        row.setDomainId("domain");
        row.setProcessId("process");
        row.setStoragePolicy(StoragePolicy.DAILY_INCREMENTAL);
        row.setLifecycleDays(30);
        row.setOwnerId("actor");
        row.setRevision(1);
        return row;
    }
}
