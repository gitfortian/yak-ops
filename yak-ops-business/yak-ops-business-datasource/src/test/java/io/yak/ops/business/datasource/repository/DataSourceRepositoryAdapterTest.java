package io.yak.ops.business.datasource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.datasource.dao.model.DataSourceSummaryRow;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceRepositoryAdapterTest {

  @Mock private DataSourceDao dao;

  @Test
  void mapsPersistenceRowToDomainIncludingRuntimeConnectionData() {
    DataSourcePO po = new DataSourcePO();
    po.setId(42L);
    po.setName("orders-db");
    po.setDbType(DataSourceDbType.MYSQL);
    po.setEnvironment(DataSourceEnvironment.PROD);
    po.setConnStatus(DataSourceConnStatus.CONNECTED);
    po.setConnectionParams("{\"host\":\"127.0.0.1\"}");
    po.setOriginalJson("{\"host\":\"127.0.0.1\"}");
    when(dao.selectById(42L)).thenReturn(po);

    DataSourceDefinition result = new DataSourceRepositoryAdapter(dao).findById(42L).orElseThrow();

    assertThat(result.getId()).isEqualTo(42L);
    assertThat(result.getDbType()).isEqualTo(DataSourceDbType.MYSQL);
    assertThat(result.getEnvironment()).isEqualTo(DataSourceEnvironment.PROD);
    assertThat(result.getConnStatus()).isEqualTo(DataSourceConnStatus.CONNECTED);
    assertThat(result.getConnectionParams()).contains("127.0.0.1");
  }

  @Test
  void mapsDaoSummaryProjectionToDomain() {
    DataSourceSummaryRow row = new DataSourceSummaryRow();
    row.setTotal(8L);
    row.setConnected(5L);
    row.setDisconnected(2L);
    row.setUnknown(1L);
    row.setEnvironmentCount(3L);
    when(dao.selectSummary()).thenReturn(row);

    DataSourceSummary result = new DataSourceRepositoryAdapter(dao).summary();

    assertThat(result.total()).isEqualTo(8L);
    assertThat(result.connected()).isEqualTo(5L);
    assertThat(result.disconnected()).isEqualTo(2L);
    assertThat(result.unknown()).isEqualTo(1L);
    assertThat(result.environmentCount()).isEqualTo(3L);
  }
}
