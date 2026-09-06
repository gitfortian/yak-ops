package io.yak.ops.business.sync.offline.engine.connector;

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
 * Resolver 只负责兼容输入优先级与标识规范化。</p>
 *
 * <p>当前产品数据源默认仍走 JDBC；是否切换到 native Connector 必须由独立 Profile
 * 变更显式完成，不能因为新增 DataSourceDbType 而改变执行语义。</p>
 */
public final class OfflineSyncConnectorRegistry {

  private static final String JDBC = "jdbc";

  private static final List<OfflineSyncConnectorProfile> PROFILES = List.of(
      profile("mysql-jdbc", DataSourceDbType.MYSQL, "JDBC-MYSQL"),
      profile("oracle-jdbc", DataSourceDbType.ORACLE, "JDBC-ORACLE"),
      profile("postgresql-jdbc", DataSourceDbType.POSTGRE_SQL, "JDBC-POSTGRESQL"),
      profile("db2-jdbc", DataSourceDbType.DB2, "JDBC-DB2"),
      profile("opengauss-jdbc", DataSourceDbType.OPEN_GAUSS, "JDBC-OPENGAUSS"),
      profile("sqlserver-jdbc", DataSourceDbType.SQL_SERVER, "JDBC-SQLSERVER"),
      profile("oceanbase-jdbc", DataSourceDbType.OCEANBASE, "JDBC-OCEANBASE"),
      profile("doris-jdbc", DataSourceDbType.DORIS, "DORIS"),
      profile("kingbase-jdbc", DataSourceDbType.KINGBASE, "JDBC-KINGBASE"),
      profile("dameng-jdbc", DataSourceDbType.DAMENG, "JDBC-DAMENG"));

  /** Historical datasource labels that remain JDBC even without a product profile. */
  private static final Set<String> LEGACY_JDBC_LABELS = Set.of(
      "JDBC",
      "MARIADB",
      "STARROCKS",
      "CLICKHOUSE",
      "HIVE",
      "DM");

  private static final Map<String, DataSourceDbType> DATA_SOURCE_ALIASES = Map.of(
      "POSTGRESQL", DataSourceDbType.POSTGRE_SQL,
      "POSTGRES", DataSourceDbType.POSTGRE_SQL,
      "OPENGAUSS", DataSourceDbType.OPEN_GAUSS,
      "SQLSERVER", DataSourceDbType.SQL_SERVER,
      "MSSQL", DataSourceDbType.SQL_SERVER);

  private static final Map<DataSourceDbType, OfflineSyncConnectorProfile> DEFAULTS =
      buildDefaults();

  private OfflineSyncConnectorRegistry() {}

  public static List<OfflineSyncConnectorProfile> profiles() {
    return PROFILES;
  }

  public static Optional<OfflineSyncConnectorProfile> defaultProfile(String value) {
    DataSourceDbType dbType = parseDbType(value);
    return dbType == null ? Optional.empty() : Optional.ofNullable(DEFAULTS.get(dbType));
  }

  /** Resolves a product/legacy datasource label to its historical default Connector ID. */
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
