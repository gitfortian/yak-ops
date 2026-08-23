package io.yak.ops.business.datasource.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.domain.plugin.DataSourcePluginDescriptor;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.service.support.DataSourcePluginViewMapper;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataSourcePluginConfigServiceImplTest {

  @Test
  void pluginConfigFlowsThroughBusinessDescriptorAndViewMapper() {
    DataSourcePluginGateway gateway = mock(DataSourcePluginGateway.class);
    DataSourcePluginViewMapper mapper = mock(DataSourcePluginViewMapper.class);
    DataSourcePluginDescriptor descriptor = descriptor();
    DataSourcePluginConfigVO expected = DataSourcePluginConfigVO.builder().pluginType("MYSQL").build();
    when(gateway.descriptor(DataSourceDbType.MYSQL)).thenReturn(descriptor);
    when(mapper.config(descriptor)).thenReturn(expected);

    DataSourcePluginConfigServiceImpl service = new DataSourcePluginConfigServiceImpl(gateway, mapper);
    DataSourcePluginConfigVO result = service.getPluginConfig("mysql");

    assertThat(result).isSameAs(expected);
    verify(gateway).descriptor(DataSourceDbType.MYSQL);
    verify(mapper).config(descriptor);
  }

  @Test
  void installCheckUsesDescriptorInsteadOfPluginRegistry() {
    DataSourcePluginGateway gateway = mock(DataSourcePluginGateway.class);
    DataSourcePluginViewMapper mapper = mock(DataSourcePluginViewMapper.class);
    when(gateway.descriptor(DataSourceDbType.MYSQL)).thenReturn(descriptor());

    DataSourcePluginConfigServiceImpl service = new DataSourcePluginConfigServiceImpl(gateway, mapper);

    assertThat(service.installPlugin("MYSQL")).isTrue();
    verify(gateway).descriptor(DataSourceDbType.MYSQL);
  }

  private DataSourcePluginDescriptor descriptor() {
    return new DataSourcePluginDescriptor(
        DataSourceDbType.MYSQL,
        "MySQL",
        "1",
        Set.of(DataSourcePluginDescriptor.Capability.CONNECTION_TEST),
        List.of(),
        List.of(),
        false,
        null);
  }
}
