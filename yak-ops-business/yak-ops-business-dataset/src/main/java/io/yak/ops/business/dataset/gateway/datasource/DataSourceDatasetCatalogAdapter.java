package io.yak.ops.business.dataset.gateway.datasource;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Adapts the typed Datasource catalog boundary to the minimal Dataset lineage contract. */
@Component
public class DataSourceDatasetCatalogAdapter implements DatasetCatalogGateway {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DataSourceDatasetCatalogAdapter.class);

  private final DataSourceCatalogReader catalogReader;

  public DataSourceDatasetCatalogAdapter(DataSourceCatalogReader catalogReader) {
    this.catalogReader = catalogReader;
  }

  @Override
  public List<CatalogColumn> listColumns(
      long dataSourceId, String databaseName, String schemaName, String tableName) {
    try {
      return catalogReader.listColumns(dataSourceId, databaseName, schemaName, tableName).stream()
          .map(column -> new CatalogColumn(column.name(), column.ordinalPosition()))
          .toList();
    } catch (RuntimeException exception) {
      LOGGER.debug(
          "Dataset lineage catalog lookup failed for datasource {} table {}.{}.{}: {}",
          dataSourceId,
          databaseName,
          schemaName,
          tableName,
          exception.getMessage());
      return List.of();
    }
  }
}
