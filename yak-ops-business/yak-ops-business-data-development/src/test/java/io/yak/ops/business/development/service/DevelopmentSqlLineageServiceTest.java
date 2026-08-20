package io.yak.ops.business.development.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.lineage.LineageAsset;
import io.yak.ops.business.lineage.LineageAssetType;
import io.yak.ops.business.lineage.LineageMaintenanceService;
import io.yak.ops.business.lineage.LineageRelationType;
import io.yak.ops.business.lineage.LineageService;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DevelopmentSqlLineageServiceTest {

  @Test
  void replacesGeneratedRelationsWithPublishedSqlLineage() {
    LineageService lineageService = mock(LineageService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    SqlTableLineageParser parser = mock(SqlTableLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    DevelopmentSqlLineageService service =
        new DevelopmentSqlLineageService(lineageService, maintenanceService, parser, objectMapper);

    String sql =
        "INSERT INTO dws.sales SELECT o.id FROM ods.orders o JOIN dim.users u ON u.id=o.user_id";
    when(parser.parse(sql)).thenReturn(new SqlTableLineageParser.ParseResult(
        List.of(
            new SqlTableLineageParser.TableRef(
                "dim.users", "dim.users", null, "dim", "users"),
            new SqlTableLineageParser.TableRef(
                "ods.orders", "ods.orders", null, "ods", "orders")),
        List.of(new SqlTableLineageParser.TableRef(
            "dws.sales", "dws.sales", null, "dws", "sales")),
        1));

    AtomicLong ids = new AtomicLong(1);
    when(lineageService.registerAsset(any())).thenAnswer(invocation -> {
      LineageService.RegisterAssetCommand command = invocation.getArgument(0);
      return new LineageAsset(
          ids.getAndIncrement(),
          command.assetKey(),
          command.assetType(),
          command.name(),
          command.sourceType(),
          command.sourceId(),
          command.parentAssetId(),
          command.dataSourceId(),
          command.databaseName(),
          command.schemaName(),
          command.tableName(),
          command.columnName(),
          command.properties(),
          Instant.parse("2026-08-20T00:00:00Z"),
          Instant.parse("2026-08-20T00:00:00Z"));
    });

    service.syncPublished(node(), revision(sql));

    verify(maintenanceService).clearRelationsByEvidence(
        DevelopmentSqlLineageService.EVIDENCE_SOURCE_TYPE, "42");

    ArgumentCaptor<LineageService.RegisterRelationCommand> relations =
        ArgumentCaptor.forClass(LineageService.RegisterRelationCommand.class);
    verify(lineageService, times(3)).registerRelation(relations.capture());

    long reads = relations.getAllValues().stream()
        .filter(value -> value.relationType() == LineageRelationType.READS_FROM)
        .count();
    long writes = relations.getAllValues().stream()
        .filter(value -> value.relationType() == LineageRelationType.WRITES_TO)
        .count();
    assertEquals(2, reads);
    assertEquals(1, writes);
    assertTrue(relations.getAllValues().stream()
        .allMatch(value -> value.sourceType().equals(
            DevelopmentSqlLineageService.EVIDENCE_SOURCE_TYPE)));
  }

  @Test
  void parserFailureClearsStaleRelationsWithoutBreakingTaskPublish() {
    LineageService lineageService = mock(LineageService.class);
    LineageMaintenanceService maintenanceService = mock(LineageMaintenanceService.class);
    SqlTableLineageParser parser = mock(SqlTableLineageParser.class);
    ObjectMapper objectMapper = new ObjectMapper();
    DevelopmentSqlLineageService service =
        new DevelopmentSqlLineageService(lineageService, maintenanceService, parser, objectMapper);

    String sql = "SELECT FROM";
    when(parser.parse(sql)).thenThrow(new SqlTableLineageParser.SqlLineageParseException(
        "parse failed", new RuntimeException("parse failed")));
    when(lineageService.registerAsset(any())).thenAnswer(invocation -> {
      LineageService.RegisterAssetCommand command = invocation.getArgument(0);
      return new LineageAsset(
          1L,
          command.assetKey(),
          LineageAssetType.SQL_TASK,
          command.name(),
          command.sourceType(),
          command.sourceId(),
          null,
          command.dataSourceId(),
          null,
          null,
          null,
          null,
          command.properties(),
          Instant.parse("2026-08-20T00:00:00Z"),
          Instant.parse("2026-08-20T00:00:00Z"));
    });

    assertDoesNotThrow(() -> service.syncPublished(node(), revision(sql)));

    verify(maintenanceService).clearRelationsByEvidence(
        DevelopmentSqlLineageService.EVIDENCE_SOURCE_TYPE, "42");
    verify(lineageService, never()).registerRelation(any());
  }

  private static DevelopmentNode node() {
    return new DevelopmentNode(
        42L,
        "sales sql",
        "SQL",
        7L,
        3L,
        true,
        Instant.parse("2026-08-20T00:00:00Z"),
        Instant.parse("2026-08-20T00:00:00Z"));
  }

  private static DevelopmentTaskRevision revision(String sql) {
    return new DevelopmentTaskRevision(
        100L,
        42L,
        3,
        9L,
        new TaskDefinition("SQL", 1, sql, "{\"dataSourceId\":\"12\"}"),
        "checksum",
        Instant.parse("2026-08-20T00:00:00Z"));
  }
}
