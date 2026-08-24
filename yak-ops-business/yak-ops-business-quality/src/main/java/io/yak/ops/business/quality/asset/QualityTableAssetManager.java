package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.TableAssetSpec;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway;
import io.yak.ops.business.quality.gateway.datasource.QualityDataCatalogGateway.QualityPhysicalTable;
import io.yak.ops.business.quality.repository.QualityTableAssetRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Command-side lifecycle for quality table registration. */
@Component
@ConditionalOnQualityEnabled
public class QualityTableAssetManager {
  private final QualityTableAssetRepository repository;
  private final QualityDataCatalogGateway catalogGateway;

  public QualityTableAssetManager(
      QualityTableAssetRepository repository,
      QualityDataCatalogGateway catalogGateway) {
    this.repository = repository;
    this.catalogGateway = catalogGateway;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public RegisterResult register(QualityTableAssetCommand.Register command, String operator) {
    if (command.dataSourceId() == null) throw new IllegalArgumentException("数据源编号无效");
    QualityTableCandidateReader.validateDataSourceId(command.dataSourceId());
    String selectedDatabase = QualityTableCandidateReader.trimToNull(command.databaseName());
    List<QualityPhysicalTable> physicalTables = catalogGateway.listTables(
        command.dataSourceId(), selectedDatabase, null, null);

    Map<String, QualityPhysicalTable> physicalTableMap = new LinkedHashMap<>();
    for (QualityPhysicalTable table : physicalTables) {
      String key = QualityTableCandidateReader.targetKey(
          QualityTableCandidateReader.firstNonBlank(table.databaseName(), selectedDatabase),
          table.schemaName(), table.tableName());
      physicalTableMap.putIfAbsent(key, table);
    }

    Map<String, QualityTableAssetCommand.Item> requestedTables = new LinkedHashMap<>();
    for (QualityTableAssetCommand.Item item : command.tables()) {
      String database = QualityTableCandidateReader.firstNonBlank(item.databaseName(), selectedDatabase);
      requestedTables.putIfAbsent(
          QualityTableCandidateReader.targetKey(database, item.schemaName(), item.tableName()), item);
    }

    String registeredBy = normalizeOperator(operator);
    List<TableAssetSpec> writes = requestedTables.entrySet().stream()
        .map(entry -> {
          QualityTableAssetCommand.Item requested = entry.getValue();
          QualityPhysicalTable physical = physicalTableMap.get(entry.getKey());
          if (physical == null) {
            throw new IllegalArgumentException(
                "数据表已不存在或无法通过数据源插件发现：" + requested.tableName());
          }
          return new TableAssetSpec(
              command.dataSourceId(),
              requireText(command.dataSourceName(), "数据源名称不能为空"),
              QualityTableCandidateReader.firstNonBlank(physical.databaseName(), selectedDatabase),
              QualityTableCandidateReader.trimToNull(physical.schemaName()),
              physical.tableName(),
              QualityTableCandidateReader.trimToNull(physical.tableType()),
              QualityTableCandidateReader.trimToNull(physical.remarks()),
              registeredBy);
        })
        .toList();

    int registered = repository.registerTableAssets(writes);
    return new RegisterResult(command.tables().size(), registered);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public boolean unregister(long id) {
    if (id <= 0L) throw new IllegalArgumentException("注册表编号无效");
    if (repository.countMonitorsForTableAsset(id) > 0) {
      throw new IllegalStateException("当前数据表已配置质量监控，请先删除监控后再取消注册");
    }
    if (!repository.deleteTableAsset(id)) {
      throw new IllegalArgumentException("注册数据表不存在：" + id);
    }
    return true;
  }

  private static String normalizeOperator(String operator) {
    String normalized = QualityTableCandidateReader.trimToNull(operator);
    return normalized == null ? "system" : normalized;
  }

  private static String requireText(String value, String message) {
    String normalized = QualityTableCandidateReader.trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  public record RegisterResult(int requested, int registered) {}
}
