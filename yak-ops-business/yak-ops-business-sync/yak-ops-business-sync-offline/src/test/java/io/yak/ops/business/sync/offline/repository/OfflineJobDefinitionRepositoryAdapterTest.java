package io.yak.ops.business.sync.offline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.dao.DataSourceDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfflineJobDefinitionRepositoryAdapterTest {

  @Mock private OfflineJobDefinitionDao dao;
  @Mock private DataSourceDao dataSourceDao;

  @Test
  void runtimeReadDoesNotLoadDatasourceDisplayMetadata() {
    OfflineJobDefinitionPO po = definition();
    when(dao.selectById(42L)).thenReturn(po);

    OfflineJobDefinition result =
        new OfflineJobDefinitionRepositoryAdapter(dao, dataSourceDao)
            .findById(42L)
            .orElseThrow();

    assertThat(result.getSourceDatasourceName()).isNull();
    assertThat(result.getSinkDatasourceName()).isNull();
    verifyNoInteractions(dataSourceDao);
  }

  @Test
  void displayReadEnrichesDatasourceNames() {
    OfflineJobDefinitionPO po = definition();
    DataSourcePO source = new DataSourcePO();
    source.setId(1L);
    source.setName("source-mysql");
    DataSourcePO sink = new DataSourcePO();
    sink.setId(2L);
    sink.setName("sink-mysql");
    when(dao.selectById(42L)).thenReturn(po);
    when(dataSourceDao.selectById(1L)).thenReturn(source);
    when(dataSourceDao.selectById(2L)).thenReturn(sink);

    OfflineJobDefinition result =
        new OfflineJobDefinitionRepositoryAdapter(dao, dataSourceDao)
            .findForViewById(42L)
            .orElseThrow();

    assertThat(result.getSourceDatasourceName()).isEqualTo("source-mysql");
    assertThat(result.getSinkDatasourceName()).isEqualTo("sink-mysql");
  }

  private OfflineJobDefinitionPO definition() {
    OfflineJobDefinitionPO po = new OfflineJobDefinitionPO();
    po.setId(42L);
    po.setJobName("demo");
    po.setSourceDatasourceId(1L);
    po.setSinkDatasourceId(2L);
    return po;
  }
}
