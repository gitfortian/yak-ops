package io.yak.ops.business.dataset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryRevisionDatasetSourceAdapterTest {

  @Test
  void executesPinnedRevisionThroughDatasourceProviderAndTrimsOverflowRow() {
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
        new TaskRevisionRef(11L, 99L, 5),
        Instant.EPOCH,
        Instant.EPOCH);
    TaskSourceRevision revision = new TaskSourceRevision(
        71L,
        3,
        new TaskDefinition("SQL", 1, "SELECT region, amount FROM sales", "{\"dataSourceId\":\"9\"}"),
        "checksum-v3");
    when(catalog.resolveRevision(11L, 71L)).thenReturn(new TaskAssetRevision(asset, revision));
    when(provider.open("9")).thenReturn(executor);
    when(executor.execute(any())).thenReturn(DataSourceSqlResult.resultSet(
        List.of(
            new DataSourceSqlColumn("region", "region", "VARCHAR", Types.VARCHAR, true),
            new DataSourceSqlColumn("amount", "amount", "DECIMAL", Types.DECIMAL, true)),
        List.of(
            List.of("east", 10),
            List.of("west", 20),
            List.of("north", 30)),
        false));

    QueryRevisionDatasetSourceAdapter adapter = new QueryRevisionDatasetSourceAdapter(
        catalog, provider, new ObjectMapper(), new DatasetQueryCompiler());
    Dataset dataset = new Dataset(21L, "sales", null, DatasetStatus.ONLINE, 31L, Instant.EPOCH, Instant.EPOCH);
    DatasetVersion version = new DatasetVersion(
        31L, 21L, 1, DatasetSourceType.QUERY_REVISION, 11L, 71L, 3, "[]", Instant.EPOCH);
    List<DatasetField> fields = List.of(
        new DatasetField("region", 31L, "region", "region", DatasetFieldDataType.STRING, true, null, DatasetFieldRole.DIMENSION, 1),
        new DatasetField("amount", 31L, "amount", "amount", DatasetFieldDataType.NUMBER, true, null, DatasetFieldRole.MEASURE, 2));
    DatasetQueryRequest request = new DatasetQueryRequest(
        null, List.of(), List.of(), List.of(), List.of(), 2, 15);

    DatasetQueryExecution execution = adapter.execute(dataset, version, fields, request);
    DatasetQueryResult result = execution.result();

    verify(catalog).resolveRevision(11L, 71L);
    ArgumentCaptor<DataSourceSqlRequest> captor = ArgumentCaptor.forClass(DataSourceSqlRequest.class);
    verify(executor).execute(captor.capture());
    assertTrue(captor.getValue().sql().contains("FROM (SELECT region, amount FROM sales) yak_dataset_source"));
    assertEquals(3, captor.getValue().maxRows());
    assertEquals(15, captor.getValue().timeoutSeconds());
    assertEquals(2, result.returnedRows());
    assertTrue(result.truncated());
    assertEquals(1, result.datasetVersionNo());
    assertEquals("9", execution.dataSourceId());
    assertEquals(captor.getValue().sql(), execution.sql());
    assertTrue(execution.prepareMillis() >= 0L);
    assertTrue(execution.waitMillis() >= 0L);
    assertTrue(execution.executeMillis() >= 0L);
    assertTrue(execution.transferMillis() >= 0L);
  }
}
