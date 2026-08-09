package io.yak.ops.business.datasource.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.util.DataSourceSecretCodec;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import org.junit.jupiter.api.Test;

class DataSourceViewMapperTest {

  @Test
  void detailKeepsJdbcAndConnectionJsonMasked() {
    DataSourcePluginRegistry registry = mock(DataSourcePluginRegistry.class);
    DataSourceSecretCodec secretCodec = mock(DataSourceSecretCodec.class);
    DataSourcePlugin plugin = mock(DataSourcePlugin.class);
    DataSourceViewMapper mapper = new DataSourceViewMapper(registry, secretCodec);

    DataSourceDefinition source = new DataSourceDefinition();
    source.setId(42L);
    source.setName("orders-db");
    source.setDbType(DataSourceDbType.MYSQL);
    source.setEnvironment(DataSourceEnvironment.PROD);
    source.setConnStatus(DataSourceConnStatus.CONNECTED);
    source.setJdbcUrl("jdbc:mysql://root:secret@127.0.0.1:3306/orders");
    source.setOriginalJson("{\"password\":\"secret\"}");

    when(registry.get(DataSourceDbType.MYSQL)).thenReturn(plugin);
    when(secretCodec.maskSensitiveText(source.getJdbcUrl()))
        .thenReturn("jdbc:mysql://root:******@127.0.0.1:3306/orders");
    when(secretCodec.maskConnectionJson(plugin, source.getOriginalJson()))
        .thenReturn("{\"password\":\"******\"}");

    DataSourceVO result = mapper.definition(source, true);

    assertThat(result.getJdbcUrl()).doesNotContain("secret");
    assertThat(result.getOriginalJson()).doesNotContain("secret");
    assertThat(result.getEnvironmentName()).isEqualTo("生产");
    verify(secretCodec).maskSensitiveText(source.getJdbcUrl());
    verify(secretCodec).maskConnectionJson(plugin, source.getOriginalJson());
  }
}
