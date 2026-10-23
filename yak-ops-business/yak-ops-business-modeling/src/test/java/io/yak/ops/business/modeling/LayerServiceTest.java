package io.yak.ops.business.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.impl.LayerServiceImpl;
import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
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
import org.springframework.dao.DuplicateKeyException;

/** Layer authorization, preset seeding, binding rules and revision contracts without external resources. */
class LayerServiceTest {
    @Mock LayerRepository layers;
    @Mock WorkspaceService workspaces;
    @InjectMocks LayerServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void rejectsInactiveActorsBeforeTouchingLayers() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);
        var error = assertThrows(BusinessException.class, () -> service.list("ws", "actor"));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(layers);
    }

    @Test
    void seedsTheFivePresetsOnceInWarehouseOrder() {
        when(layers.builtinCount("ws")).thenReturn(0L);
        when(layers.layers("ws")).thenReturn(List.of());

        service.list("ws", "actor");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LayerEntity>> seeded = ArgumentCaptor.forClass(List.class);
        verify(layers).seed(seeded.capture());
        assertEquals(
                List.of("ODS", "DWD", "DWS", "ADS", "DIM"),
                seeded.getValue().stream().map(LayerEntity::getEnCode).toList());
        assertTrue(seeded.getValue().stream().allMatch(LayerEntity::isBuiltin));
        assertTrue(seeded.getValue().stream().noneMatch(LayerEntity::isEnabled));
        assertTrue(seeded.getValue().stream().allMatch(row -> row.getDataSourceId() == null));
    }

    @Test
    void neverReseedsAWorkspaceThatAlreadyHasPresets() {
        when(layers.builtinCount("ws")).thenReturn(5L);
        when(layers.layers("ws")).thenReturn(List.of());

        service.list("ws", "actor");

        verify(layers, never()).seed(anyList());
    }

    @Test
    void projectsTheBoundDatasourceName() {
        when(layers.builtinCount("ws")).thenReturn(5L);
        var preset = preset("ODS", true, "ds-1");
        when(layers.layers("ws")).thenReturn(List.of(preset));
        when(layers.datasources("ws")).thenReturn(List.of(datasource("ds-1", "主仓")));

        var rows = service.list("ws", "actor");

        assertEquals(1, rows.size());
        assertEquals("主仓", rows.getFirst().dataSourceName());
        assertTrue(rows.getFirst().enabled());
        assertTrue(rows.getFirst().builtin());
    }

    @Test
    void exposesOnlyEnabledLayersForModeling() {
        when(layers.builtinCount("ws")).thenReturn(5L);
        when(layers.layers("ws")).thenReturn(List.of(preset("ODS", true, "ds-1"), preset("DWD", false, "ds-1")));
        when(layers.datasources("ws")).thenReturn(List.of(datasource("ds-1", "主仓")));

        var options = service.options("ws", "actor");

        assertEquals(1, options.size());
        assertEquals("ODS", options.getFirst().enCode());
        assertEquals("ds-1", options.getFirst().dataSourceId());
    }

    @Test
    void normalizesALowercaseAbbreviationOnCreate() {
        when(layers.datasourceExists("ws", "ds-1")).thenReturn(true);
        when(layers.datasources("ws")).thenReturn(List.of(datasource("ds-1", "主仓")));

        var saved = service.create("ws", "actor", input("明细宽表", "dwd_wide", true, "ds-1"));

        assertEquals("DWD_WIDE", saved.enCode());
        assertFalse(saved.builtin());
        assertEquals(1, saved.revision());
    }

    @Test
    void reportsADuplicateAbbreviation() {
        doThrow(new DuplicateKeyException("uk_modeling_layer_code")).when(layers).add(any());

        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("重复层", "TMP", false, null)));

        assertEquals(LayerErrorCode.DUPLICATE_CODE, error.getErrorCode());
    }

    @Test
    void refusesACustomLayerThatTakesAPresetAbbreviation() {
        var error = assertThrows(BusinessException.class, () -> service.create("ws", "actor", input("贴源", "ods", false, null)));

        assertEquals(LayerErrorCode.DUPLICATE_CODE, error.getErrorCode());
        verify(layers, never()).add(any());
    }

    @Test
    void refusesRenamingACustomLayerOntoAPresetAbbreviation() {
        var custom = preset("LOCAL", false, null);
        custom.setBuiltin(false);
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(custom));

        var update = new UpdateLayerDTO();
        update.setCnName("改名层");
        update.setEnCode("DWD");
        update.setRevision(1L);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer-1", update));

        assertEquals(LayerErrorCode.DUPLICATE_CODE, error.getErrorCode());
        verify(layers, never()).update(any());
    }

    @Test
    void requiresABoundDatasourceBeforeEnabling() {
        var error = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("贴源", "ODS2", true, null)));

        assertEquals(LayerErrorCode.DATASOURCE_REQUIRED, error.getErrorCode());
        verify(layers, never()).add(any());
    }

    @Test
    void rejectsADatasourceOutsideTheWorkspace() {
        when(layers.datasourceExists("ws", "ds-x")).thenReturn(false);

        var error = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("贴源", "ODS2", false, "ds-x")));

        assertEquals(LayerErrorCode.INVALID_DATASOURCE, error.getErrorCode());
    }

    @Test
    void rejectsAbbreviationsThatBreakTheUppercaseContract() {
        var error = assertThrows(
                BusinessException.class, () -> service.create("ws", "actor", input("贴源", "1_ODS", false, null)));

        assertEquals(LayerErrorCode.INVALID_CODE, error.getErrorCode());
    }

    @Test
    void keepsPresetNameCodeAndDescriptionImmutable() {
        var preset = preset("ODS", false, null);
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(preset));

        var error = assertThrows(
                BusinessException.class, () -> service.update("ws", "actor", "layer-1", update("贴源层改", "ODS", 1)));

        assertEquals(LayerErrorCode.BUILTIN_IMMUTABLE, error.getErrorCode());
        verify(layers, never()).update(any());
    }

    @Test
    void allowsBindingAndEnablingAPreset() {
        var preset = preset("ODS", false, null);
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(preset));
        when(layers.datasourceExists("ws", "ds-1")).thenReturn(true);
        when(layers.update(preset)).thenReturn(true);
        when(layers.datasources("ws")).thenReturn(List.of(datasource("ds-1", "主仓")));

        var saved = service.update("ws", "actor", "layer-1", updateWithBinding(1));

        assertEquals(2, saved.revision());
        assertEquals("ds-1", saved.dataSourceId());
        assertTrue(saved.enabled());
        assertEquals("主仓", saved.dataSourceName());
    }

    @Test
    void refusesToDeleteAPreset() {
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(preset("ODS", false, null)));

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "layer-1", 1));

        assertEquals(LayerErrorCode.BUILTIN_UNDELETABLE, error.getErrorCode());
        verify(layers, never()).delete(anyString(), anyString(), anyLong());
    }

    @Test
    void rejectsAStaleRevisionOnDelete() {
        var custom = preset("CUSTOM", false, null);
        custom.setBuiltin(false);
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(custom));

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "layer-1", 7));

        assertEquals(LayerErrorCode.REVISION_CONFLICT, error.getErrorCode());
    }

    @Test
    void deletesACustomLayerWithTheMatchingRevision() {
        var custom = preset("CUSTOM", false, null);
        custom.setBuiltin(false);
        when(layers.layer("ws", "layer-1")).thenReturn(Optional.of(custom));
        when(layers.delete("ws", "layer-1", 1)).thenReturn(true);

        service.delete("ws", "actor", "layer-1", 1);

        verify(layers).delete("ws", "layer-1", 1);
    }

    @Test
    void reportsAbbreviationAvailabilityInsideTheWorkspace() {
        when(layers.occupied("ws", "ODS", null)).thenReturn(true);
        when(layers.occupied("ws", "FREE", null)).thenReturn(false);

        assertFalse(service.available("ws", "actor", "ods", null));
        assertTrue(service.available("ws", "actor", "free", null));
    }

    @Test
    void rejectsAnUnknownLayerAsMissing() {
        when(layers.layer("ws", "missing")).thenReturn(Optional.empty());

        var error = assertThrows(BusinessException.class, () -> service.delete("ws", "actor", "missing", 1));

        assertEquals(CommonErrorCode.RESOURCE_NOT_EXISTS, error.getErrorCode());
    }

    @Test
    void normalizesABlankDescriptionAndDatasource() {
        var saved = service.create("ws", "actor", input("临时层", "TMP", false, "  "));

        assertNull(saved.dataSourceId());
        assertNull(saved.description());
    }

    private static LayerEntity preset(String enCode, boolean enabled, String dataSourceId) {
        var row = new LayerEntity();
        row.setId("layer-1");
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        row.setCnName("贴源层");
        row.setDataSourceId(dataSourceId);
        row.setEnabled(enabled);
        row.setBuiltin(true);
        row.setSortNo(10);
        row.setRevision(1);
        return row;
    }

    private static DataSourceEntity datasource(String id, String name) {
        var row = new DataSourceEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setName(name);
        row.setDbType("MYSQL");
        row.setEnvironment(DataSourceEnvironment.PROD);
        row.setConnStatus(DataSourceConnStatus.CONNECTED);
        return row;
    }

    private static LayerInputDTO input(String cnName, String enCode, boolean enabled, String dataSourceId) {
        var input = new LayerInputDTO();
        input.setCnName(cnName);
        input.setEnCode(enCode);
        input.setEnabled(enabled);
        input.setDataSourceId(dataSourceId);
        return input;
    }

    private static UpdateLayerDTO update(String cnName, String enCode, long revision) {
        var input = new UpdateLayerDTO();
        input.setCnName(cnName);
        input.setEnCode(enCode);
        input.setEnabled(false);
        input.setRevision(revision);
        return input;
    }

    private static UpdateLayerDTO updateWithBinding(long revision) {
        var input = new UpdateLayerDTO();
        input.setCnName("贴源层");
        input.setEnCode("ODS");
        input.setDataSourceId("ds-1");
        input.setEnabled(true);
        input.setRevision(revision);
        return input;
    }
}
