package io.yak.ops.common.enums.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DataSourceDbTypeTest {

  @Test
  void shouldSupportStableDatasourceAliases() {
    assertThat(DataSourceDbType.parse("postgresql"))
        .isEqualTo(DataSourceDbType.POSTGRE_SQL);
    assertThat(DataSourceDbType.parse("POSTGRES"))
        .isEqualTo(DataSourceDbType.POSTGRE_SQL);
    assertThat(DataSourceDbType.parse("opengauss"))
        .isEqualTo(DataSourceDbType.OPEN_GAUSS);
    assertThat(DataSourceDbType.parse("SQLSERVER"))
        .isEqualTo(DataSourceDbType.SQL_SERVER);
    assertThat(DataSourceDbType.parse("mssql"))
        .isEqualTo(DataSourceDbType.SQL_SERVER);
    assertThat(DataSourceDbType.parse("yashandb"))
        .isEqualTo(DataSourceDbType.YASHAN_DB);
    assertThat(DataSourceDbType.parse("yasdb"))
        .isEqualTo(DataSourceDbType.YASHAN_DB);
    assertThat(DataSourceDbType.parse("high-go"))
        .isEqualTo(DataSourceDbType.HIGHGO);
    assertThat(DataSourceDbType.parse("hgdb"))
        .isEqualTo(DataSourceDbType.HIGHGO);
    assertThat(DataSourceDbType.parse("intersystems-iris"))
        .isEqualTo(DataSourceDbType.IRIS);
    assertThat(DataSourceDbType.parse("xugudb"))
        .isEqualTo(DataSourceDbType.XUGU);
    assertThat(DataSourceDbType.parse("duck-db"))
        .isEqualTo(DataSourceDbType.DUCKDB);
    assertThat(DataSourceDbType.parse("es7"))
        .isEqualTo(DataSourceDbType.ELASTICSEARCH7);
    assertThat(DataSourceDbType.parse("elasticsearch-8"))
        .isEqualTo(DataSourceDbType.ELASTICSEARCH8);
  }

  @Test
  void shouldExposeCurrentDatasourceTypesAsFirstClassTypes() {
    assertThat(DataSourceDbType.values())
        .contains(
            DataSourceDbType.DB2,
            DataSourceDbType.OPEN_GAUSS,
            DataSourceDbType.SQL_SERVER,
            DataSourceDbType.OCEANBASE,
            DataSourceDbType.YASHAN_DB,
            DataSourceDbType.HIGHGO,
            DataSourceDbType.IRIS,
            DataSourceDbType.XUGU,
            DataSourceDbType.DUCKDB,
            DataSourceDbType.DORIS,
            DataSourceDbType.STARROCKS,
            DataSourceDbType.CLICKHOUSE,
            DataSourceDbType.ELASTICSEARCH7,
            DataSourceDbType.ELASTICSEARCH8);
    assertThat(DataSourceDbType.YASHAN_DB.getDisplayName()).isEqualTo("YashanDB");
    assertThat(DataSourceDbType.HIGHGO.getDisplayName()).isEqualTo("HighGo");
    assertThat(DataSourceDbType.IRIS.getDisplayName()).isEqualTo("InterSystems IRIS");
    assertThat(DataSourceDbType.XUGU.getDisplayName()).isEqualTo("XuguDB");
    assertThat(DataSourceDbType.DUCKDB.getDisplayName()).isEqualTo("DuckDB");
    assertThat(DataSourceDbType.STARROCKS.getDisplayName()).isEqualTo("StarRocks");
    assertThat(DataSourceDbType.CLICKHOUSE.getDisplayName()).isEqualTo("ClickHouse");
    assertThat(DataSourceDbType.ELASTICSEARCH7.getDisplayName()).isEqualTo("Elasticsearch 7");
    assertThat(DataSourceDbType.ELASTICSEARCH8.getDisplayName()).isEqualTo("Elasticsearch 8");
  }

  @Test
  void shouldKeepInfrastructureDefaultsOutsideEnum() {
    assertThat(DataSourceDbType.MYSQL.getDisplayName()).isEqualTo("MySQL");
    assertThat(DataSourceDbType.DORIS.getDisplayName()).isEqualTo("Doris");
  }

  @Test
  void shouldRejectUnsupportedTypeWithoutBusinessDependency() {
    assertThatThrownBy(() -> DataSourceDbType.parse("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不支持的数据源类型");
  }
}
