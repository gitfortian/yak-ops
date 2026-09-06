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
  }

  @Test
  void shouldExposeJdbcWaveOneAsFirstClassTypes() {
    assertThat(DataSourceDbType.values())
        .contains(
            DataSourceDbType.DB2,
            DataSourceDbType.OPEN_GAUSS,
            DataSourceDbType.SQL_SERVER,
            DataSourceDbType.OCEANBASE);
    assertThat(DataSourceDbType.OPEN_GAUSS.getDisplayName()).isEqualTo("openGauss");
    assertThat(DataSourceDbType.SQL_SERVER.getDisplayName()).isEqualTo("SQL Server");
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
