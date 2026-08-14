package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatasetSchemaDiscoveryServiceTest {

  @Test
  void discoversStableFieldsFromPinnedRevision() {
    TaskCatalogService catalog = mock(TaskCatalogService.class);
    DataSourceExecutionProvider provider = mock(DataSourceExecutionProvider.class);
    DataSourceSqlExecutor executor = mock(DataSourceSqlExecutor.class);
    TaskAsset asset = new TaskAsset(
        11L,
        TaskAssetSource.DATA_DEVELOPMENT,
        "101",
        null,
        "sales.sql",
        "SQL",
        TaskAssetStatus.ONLINE,
        new TaskRevisionRef(11L, 71L, 3),
        Instant.EPOCH,
        Instant.EPOCH);
    TaskSourceRevision revision = new TaskSourceRevision(
        71L,
        3,
        new TaskDefinition("SQL", 1, "SELECT region, amount FROM sales", "{\"dataSourceId\":\"9\"}"),
        "checksum");
    when(catalog.resolveRevision(11L, 71L)).thenReturn(new TaskAssetRevision(asset, revision));
    when(provider.open("9")).thenReturn(executor);
    when(executor.execute(any())).thenReturn(DataSourceSqlResult.query(
        List.of(
            new DataSourceSqlColumn("region", "region", "VARCHAR", Types.VARCHAR, true),
            new DataSourceSqlColumn("amount", "amount", "DECIMAL", Types.DECIMAL, true)),
        List.of(),
        false));

    DatasetSchemaDiscoveryService service = new DatasetSchemaDiscoveryService(catalog, provider, new ObjectMapper());
    List<DatasetService.FieldSpec> fields = service.discover(21L, asset);

    assertEquals(2, fields.size());
    assertEquals(DatasetFieldDataType.STRING, fields.get(0).dataType());
    assertEquals(DatasetFieldRole.DIMENSION, fields.get(0).defaultRole());
    assertEquals(DatasetFieldDataType.NUMBER, fields.get(1).dataType());
    assertEquals(DatasetFieldRole.MEASURE, fields.get(1).defaultRole());
    assertEquals(fields.get(0).fieldId(), service.discover(21L, asset).get(0).fieldId());
  }
}
