package io.yak.ops.common.enums.datasource;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Yak Ops 当前支持的数据源类型。具体驱动、端口和 URL 规则由插件提供。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceDbType {

  MYSQL("MySQL"),
  ORACLE("Oracle"),
  POSTGRE_SQL("PostgreSQL"),
  DB2("IBM Db2"),
  OPEN_GAUSS("openGauss"),
  SQL_SERVER("SQL Server"),
  OCEANBASE("OceanBase"),
  YASHAN_DB("YashanDB"),
  HIGHGO("HighGo"),
  IRIS("InterSystems IRIS"),
  XUGU("XuguDB"),
  DUCKDB("DuckDB"),
  DORIS("Doris"),
  STARROCKS("StarRocks"),
  CLICKHOUSE("ClickHouse"),
  ELASTICSEARCH7("Elasticsearch 7"),
  ELASTICSEARCH8("Elasticsearch 8"),
  KINGBASE("KingbaseES"),
  DAMENG("达梦");

  private final String displayName;

  public static DataSourceDbType parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("数据源类型不能为空");
    }

    String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    if ("POSTGRESQL".equals(normalized) || "POSTGRES".equals(normalized)) {
      normalized = "POSTGRE_SQL";
    } else if ("OPENGAUSS".equals(normalized)) {
      normalized = "OPEN_GAUSS";
    } else if ("SQLSERVER".equals(normalized) || "MSSQL".equals(normalized)) {
      normalized = "SQL_SERVER";
    } else if ("YASHANDB".equals(normalized) || "YASDB".equals(normalized)) {
      normalized = "YASHAN_DB";
    } else if ("HIGH_GO".equals(normalized) || "HGDB".equals(normalized)) {
      normalized = "HIGHGO";
    } else if ("INTERSYSTEMS_IRIS".equals(normalized)) {
      normalized = "IRIS";
    } else if ("XUGUDB".equals(normalized)) {
      normalized = "XUGU";
    } else if ("DUCK_DB".equals(normalized)) {
      normalized = "DUCKDB";
    } else if ("ELASTICSEARCH_7".equals(normalized) || "ES7".equals(normalized)) {
      normalized = "ELASTICSEARCH7";
    } else if ("ELASTICSEARCH_8".equals(normalized) || "ES8".equals(normalized)) {
      normalized = "ELASTICSEARCH8";
    }

    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("不支持的数据源类型：" + value, exception);
    }
  }
}
