package io.yak.ops.business.modeling.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.naming.impl.NamingRuleServiceImpl;
import io.yak.ops.common.bean.dto.modeling.NamingRuleUpdateDTO;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.LayerErrorCode;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.LayerEntity;
import io.yak.ops.dao.entity.modeling.standard.StandardNamingEntryEntity;
import io.yak.ops.dao.repository.modeling.LayerRepository;
import io.yak.ops.dao.repository.modeling.ModelNamingRow;
import io.yak.ops.dao.repository.modeling.ModelRepository;
import io.yak.ops.dao.repository.modeling.standard.StandardNamingEntryRepository;
import io.yak.ops.workspace.WorkspaceService;
import io.yak.ops.workspace.enums.WorkspaceErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Layer naming-rule enablement, violation reporting and root-word backfill without external resources. */
class NamingRuleServiceTest {
    @Mock LayerRepository layers;
    @Mock ModelRepository models;
    @Mock StandardNamingEntryRepository entries;
    @Mock WorkspaceService workspaces;
    @InjectMocks NamingRuleServiceImpl service;
    AutoCloseable mocks;

    @BeforeEach
    void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(true);
        when(layers.layer("ws", "layer")).thenReturn(Optional.of(layer(3)));
    }

    @AfterEach
    void close() throws Exception {
        mocks.close();
    }

    @Test
    void reportsModelsWhoseSuffixIgnoresTheDictionary() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        when(models.namingRows("ws", "layer"))
                .thenReturn(List.of(
                        row("m1", "dwd_trade_order", "order"), row("m2", "dwd_legacy_order", "order")));
        var rule = service.rule("ws", "actor", "layer");
        assertFalse(rule.enabled());
        assertEquals(3, rule.revision());
        assertEquals(1, rule.violations().size());
        assertEquals("m2", rule.violations().getFirst().modelId());
    }

    @Test
    void refusesEnablementWhenTheDictionaryIsEmpty() {
        when(entries.entries("ws")).thenReturn(List.of());
        when(models.namingRows("ws", "layer")).thenReturn(List.of());
        var error =
                assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer", update(true, false)));
        assertEquals(LayerErrorCode.INVALID_NAMING_RULE, error.getErrorCode());
        verify(layers, never()).updateNamingRule(anyString(), anyString(), anyBoolean(), eq(3L), anyString());
    }

    @Test
    void refusesEnablementWhenAProcessCannotFitTheShortestRoot() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        when(models.namingRows("ws", "layer"))
                .thenReturn(List.of(row("m1", "dwd_trade_order", "a".repeat(40))));
        var error =
                assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer", update(true, true)));
        assertEquals(LayerErrorCode.INVALID_NAMING_RULE, error.getErrorCode());
    }

    @Test
    void requiresAcknowledgementBeforeEnablingOverViolations() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        when(models.namingRows("ws", "layer")).thenReturn(List.of(row("m2", "dwd_legacy_order", "order")));
        var error =
                assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer", update(true, false)));
        assertEquals(LayerErrorCode.INVALID_NAMING_RULE, error.getErrorCode());
        verify(layers, never()).updateNamingRule(anyString(), anyString(), anyBoolean(), eq(3L), anyString());
    }

    @Test
    void recordsTheMatchedRootWordOnlyWhereItChanged() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        when(models.namingRows("ws", "layer"))
                .thenReturn(List.of(
                        new ModelNamingRow("m1", ModelType.DWD, "dwd_trade_order", "交易订单", "order", null),
                        new ModelNamingRow("m2", ModelType.DWS, "dws_trade_order_1d", "交易订单日汇总", "order", "root"),
                        row("m3", "dwd_legacy_order", "order")));
        when(layers.updateNamingRule("ws", "layer", true, 3, "actor")).thenReturn(true);
        service.update("ws", "actor", "layer", update(true, true));
        verify(models).backfillNamingRoot("ws", "m1", "root");
        verify(models, never()).backfillNamingRoot("ws", "m2", "root");
        verify(models, never()).backfillNamingRoot("ws", "m3", "root");
    }

    @Test
    void disablingSkipsTheEnablementChecks() {
        var stored = layer(3);
        stored.setNamingRuleEnabled(true);
        when(layers.layer("ws", "layer")).thenReturn(Optional.of(stored));
        when(entries.entries("ws")).thenReturn(List.of());
        when(models.namingRows("ws", "layer")).thenReturn(List.of(row("m2", "dwd_legacy_order", "order")));
        when(layers.updateNamingRule("ws", "layer", false, 3, "actor"))
                .thenAnswer(call -> {
                    stored.setNamingRuleEnabled(false);
                    stored.setRevision(4);
                    return true;
                });
        var rule = service.update("ws", "actor", "layer", update(false, false));
        assertFalse(rule.enabled());
        verify(models, never()).backfillNamingRoot(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsAStaleRevision() {
        var input = update(true, true);
        input.setRevision(2L);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer", input));
        assertEquals(LayerErrorCode.REVISION_CONFLICT, error.getErrorCode());
    }

    @Test
    void rejectsAMissingRevision() {
        var input = update(true, true);
        input.setRevision(null);
        var error = assertThrows(BusinessException.class, () -> service.update("ws", "actor", "layer", input));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());
    }

    @Test
    void derivesTheRootWordOfAConformingSuffix() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        assertEquals("root", service.rootWord("ws", "trade_order_1d", "order"));
    }

    @Test
    void refusesASuffixWithoutADictionaryRoot() {
        when(entries.entries("ws")).thenReturn(List.of(entry("root", "trade")));
        var error = assertThrows(BusinessException.class, () -> service.rootWord("ws", "legacy_order", "order"));
        assertEquals(DimensionModelingErrorCode.NAMING_RULE_VIOLATION, error.getErrorCode());
    }

    @Test
    void rejectsAnInactiveActor() {
        when(workspaces.isActiveMember("ws", "actor")).thenReturn(false);
        var error = assertThrows(BusinessException.class, () -> service.rule("ws", "actor", "layer"));
        assertEquals(WorkspaceErrorCode.ACCESS_DENIED, error.getErrorCode());
        verifyNoInteractions(layers, models, entries);
    }

    private static NamingRuleUpdateDTO update(boolean enabled, boolean acknowledged) {
        var input = new NamingRuleUpdateDTO();
        input.setEnabled(enabled);
        input.setAcknowledgeViolations(acknowledged);
        input.setRevision(3L);
        return input;
    }

    private static LayerEntity layer(long revision) {
        var row = new LayerEntity();
        row.setId("layer");
        row.setWorkspaceId("ws");
        row.setEnCode("DWD");
        row.setRevision(revision);
        return row;
    }

    private static StandardNamingEntryEntity entry(String id, String enCode) {
        var row = new StandardNamingEntryEntity();
        row.setId(id);
        row.setWorkspaceId("ws");
        row.setEnCode(enCode);
        return row;
    }

    private static ModelNamingRow row(String id, String name, String processCode) {
        return new ModelNamingRow(id, ModelType.DWD, name, "业务名称", processCode, null);
    }
}
