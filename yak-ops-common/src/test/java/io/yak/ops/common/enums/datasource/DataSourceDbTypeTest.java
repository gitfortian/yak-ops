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
    assertThat(DataSourceDbType.parse("es7"))
        .isEqualTo(DataSourceDbType.ELASTICSEARCH7);
    assertThat(DataSourceDbType.parse("elasticsearch-8"))
        .isEqualTo(DataSourceDbType.ELASTICSEARCH8);
  }

  @Test
  void shouldExposeJdbcNativeOlapAndVersionedSearchTypesAsFirstClassTypes() {
    assertThat(DataSourceDbType.values())
        .contains(
            DataSourceDbType.DB2,
            DataSourceDbType.OPEN_GAUSS,
            DataSourceDbType.SQL_SERVER,
            DataSourceDbType.OCEANBASE,
            DataSourceDbType.DORIS,
            DataSourceDbType.STARROCKS,
            DataSourceDbType.CLICKHOUSE,
            DataSourceDbType.ELASTICSEARCH7,
            DataSourceDbType.ELASTICSEARCH8);
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
