package io.yak.ops.business.sync.offline.connector;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 离线同步 Connector Profile 注册表。
 *
 * <p>这里是 Yak Ops 控制面默认 {@code dbType -> connectorId} 关系的单一后端来源。
 * 旧版本曾由 {@code ConnectorIdResolver} 自己维护 JDBC 类型集合；Registry 建立后，
 * Resolver 只负责兼容输入优先级与标识规范化。</p>
 *
 * <p>当前阶段严格保持历史执行语义：现有六类产品数据源默认仍走 JDBC。
 * StarRocks / ClickHouse / DB2 等尚未进入 Yak Ops 数据源类型模型的历史标签也继续按
 * 旧行为解析为 JDBC，避免本次重构偷偷改变存量定义。</p>
 *
 * @author weifuwan
 */
public final class OfflineSyncConnectorRegistry {

  private static final String JDBC = "jdbc";

  private static final List<OfflineSyncConnectorProfile> PROFILES = List.of(
      profile("mysql-jdbc", DataSourceDbType.MYSQL, "JDBC-MYSQL"),
      profile("oracle-jdbc", DataSourceDbType.ORACLE, "JDBC-ORACLE"),
      profile("postgresql-jdbc", DataSourceDbType.POSTGRE_SQL, "JDBC-POSTGRESQL"),
      profile("doris-jdbc", DataSourceDbType.DORIS, "DORIS"),
      profile("kingbase-jdbc", DataSourceDbType.KINGBASE, "JDBC-KINGBASE"),
      profile("dameng-jdbc", DataSourceDbType.DAMENG, "JDBC-DAMENG"));

  /**
   * Compatibility labels accepted by the old front/back resolver although Yak Ops currently does
   * not expose all of them as DataSourceDbType values.
   */
  private static final Set<String> LEGACY_JDBC_LABELS = Set.of(
      "JDBC",
      "MARIADB",
      "SQLSERVER",
      "SQL_SERVER",
      "STARROCKS",
      "CLICKHOUSE",
      "DB2",
      "HIVE",
      "DM");

  private static final Map<String, DataSourceDbType> DATA_SOURCE_ALIASES = Map.of(
      "POSTGRESQL", DataSourceDbType.POSTGRE_SQL,
      "POSTGRES", DataSourceDbType.POSTGRE_SQL);

  private static final Map<DataSourceDbType, OfflineSyncConnectorProfile> DEFAULTS =
      buildDefaults();

  private OfflineSyncConnectorRegistry() {
  }

  public static List<OfflineSyncConnectorProfile> profiles() {
    return PROFILES;
  }

  public static Optional<OfflineSyncConnectorProfile> defaultProfile(String value) {
    DataSourceDbType dbType = parseDbType(value);
    return dbType == null ? Optional.empty() : Optional.ofNullable(DEFAULTS.get(dbType));
  }

  /**
   * Resolves a product/legacy datasource label to its historical default Connector ID.
   * Unknown labels stay empty so framework-neutral connector identifiers can pass through.
   */
  public static Optional<String> defaultConnectorId(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return Optional.empty();
    }

    Optional<OfflineSyncConnectorProfile> profile = defaultProfile(normalized);
    if (profile.isPresent()) {
      OfflineSyncConnectorProfile selected = profile.get();
      if (!selected.sourceConnectorId().equals(selected.sinkConnectorId())) {
        throw new IllegalStateException(
            "当前兼容解析需要 Source/Sink 默认 Connector 一致：" + selected.profileId());
      }
      return Optional.of(selected.sourceConnectorId());
    }

    return LEGACY_JDBC_LABELS.contains(normalized)
        ? Optional.of(JDBC)
        : Optional.empty();
  }

  public static boolean isLegacyDatasourceLabel(String value) {
    return defaultConnectorId(value).isPresent();
  }

  private static OfflineSyncConnectorProfile profile(
      String profileId,
      DataSourceDbType dbType,
      String pluginName) {
    return new OfflineSyncConnectorProfile(
        profileId,
        dbType,
        JDBC,
        JDBC,
        pluginName,
        true);
  }

  private static Map<DataSourceDbType, OfflineSyncConnectorProfile> buildDefaults() {
    Map<DataSourceDbType, OfflineSyncConnectorProfile> result =
        new EnumMap<>(DataSourceDbType.class);
    Map<String, OfflineSyncConnectorProfile> profileIds = new LinkedHashMap<>();

    for (OfflineSyncConnectorProfile profile : PROFILES) {
      OfflineSyncConnectorProfile duplicateId = profileIds.put(profile.profileId(), profile);
      if (duplicateId != null) {
        throw new IllegalStateException("重复的离线同步 profileId：" + profile.profileId());
      }
      if (!profile.defaultProfile()) {
        continue;
      }
      OfflineSyncConnectorProfile duplicateDefault = result.put(profile.dbType(), profile);
      if (duplicateDefault != null) {
        throw new IllegalStateException(
            "数据源存在多个默认离线同步 Profile：" + profile.dbType());
      }
    }

    return Map.copyOf(result);
  }

  private static DataSourceDbType parseDbType(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    DataSourceDbType alias = DATA_SOURCE_ALIASES.get(normalized);
    if (alias != null) {
      return alias;
    }
    try {
      return DataSourceDbType.valueOf(normalized);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static String normalize(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
  }
}
