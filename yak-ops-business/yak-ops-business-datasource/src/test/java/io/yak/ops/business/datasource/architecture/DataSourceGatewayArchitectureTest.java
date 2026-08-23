package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.datasource.gateway.DataSourceCatalogGateway;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.gateway.adapter.SpiDataSourceCatalogGateway;
import io.yak.ops.business.datasource.gateway.adapter.SpiDataSourcePluginGateway;
import io.yak.ops.business.datasource.service.impl.DataSourceCatalogServiceImpl;
import io.yak.ops.business.datasource.service.impl.DataSourceServiceImpl;
import io.yak.ops.business.datasource.service.support.DataSourceViewMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceGatewayArchitectureTest {

  private static final String[] PORT_FORBIDDEN = {
    ".spi.datasource",
    ".bean.dto.",
    ".bean.vo.",
    ".bean.po.",
    "com.baomidou.mybatisplus"
  };

  @Test
  void businessGatewayContractsDoNotExposePluginSpiOrTransportPersistenceModels() {
    for (Class<?> port : List.of(DataSourcePluginGateway.class, DataSourceCatalogGateway.class)) {
      for (Method method : port.getMethods()) {
        assertTypeAvoids(port, method.getName(), method.getGenericReturnType(), PORT_FORBIDDEN);
        for (Type parameter : method.getGenericParameterTypes()) {
          assertTypeAvoids(port, method.getName(), parameter, PORT_FORBIDDEN);
        }
      }
    }
  }

  @Test
  void applicationConsumersDependOnBusinessGatewaysInsteadOfPluginInfrastructure() {
    for (Class<?> type :
        List.of(
            DataSourceServiceImpl.class,
            DataSourceCatalogServiceImpl.class,
            DataSourceViewMapper.class)) {
      for (Field field : type.getDeclaredFields()) {
        String fieldType = field.getGenericType().getTypeName();
        assertThat(fieldType)
            .as(type.getSimpleName() + "." + field.getName())
            .doesNotContain(".spi.datasource")
            .doesNotContain(".plugin.DataSourcePluginRegistry")
            .doesNotContain(".util.DataSourceSecretCodec");
      }
    }
  }

  @Test
  void spiAdaptersImplementBusinessGatewayPorts() {
    assertThat(DataSourcePluginGateway.class.isAssignableFrom(SpiDataSourcePluginGateway.class))
        .isTrue();
    assertThat(DataSourceCatalogGateway.class.isAssignableFrom(SpiDataSourceCatalogGateway.class))
        .isTrue();
  }

  private void assertTypeAvoids(
      Class<?> owner,
      String member,
      Type type,
      String... forbidden) {
    String signature = typeName(type);
    for (String packagePart : forbidden) {
      assertThat(signature)
          .as("%s.%s must not expose %s", owner.getSimpleName(), member, packagePart)
          .doesNotContain(packagePart);
    }
  }

  private String typeName(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      StringBuilder value = new StringBuilder(parameterized.getRawType().getTypeName());
      for (Type argument : parameterized.getActualTypeArguments()) {
        value.append('<').append(typeName(argument)).append('>');
      }
      return value.toString();
    }
    return type.getTypeName();
  }
}
