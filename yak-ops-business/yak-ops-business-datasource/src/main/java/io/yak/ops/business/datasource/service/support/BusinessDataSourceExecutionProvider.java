package io.yak.ops.business.datasource.service.support;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import org.springframework.stereotype.Component;

/** Resolves platform datasource IDs without exposing connection credentials to Task Plugins. */
@Component
@ConditionalOnDataSourceEnabled
public class BusinessDataSourceExecutionProvider implements DataSourceExecutionProvider {

  private final DataSourceRepository repository;
  private final DataSourcePluginRegistry pluginRegistry;
  private final DataSourceProperties properties;

  public BusinessDataSourceExecutionProvider(
      DataSourceRepository repository,
      DataSourcePluginRegistry pluginRegistry,
      DataSourceProperties properties) {
    this.repository = repository;
    this.pluginRegistry = pluginRegistry;
    this.properties = properties;
  }

  @Override
  public DataSourceSqlExecutor open(String dataSourceReference) {
    long dataSourceId = parseDataSourceId(dataSourceReference);
    DataSourceDefinition definition =
        repository
            .findById(dataSourceId)
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dataSourceReference));
    DataSourcePlugin plugin = pluginRegistry.get(definition.getDbType());
    DataSourceConnection connection = plugin.parseConnection(definition.getConnectionParams());
    return plugin.createSqlExecutor(
        connection,
        Math.max(1, properties.getConnectionTest().getTimeoutSeconds()));
  }

  private long parseDataSourceId(String reference) {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("数据源 ID 不能为空");
    }
    try {
      long value = Long.parseLong(reference.trim());
      if (value <= 0L) throw new NumberFormatException("non-positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("数据源 ID 非法：" + reference, exception);
    }
  }
}
