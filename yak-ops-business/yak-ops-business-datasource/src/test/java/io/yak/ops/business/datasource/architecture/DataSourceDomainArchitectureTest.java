package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceDomainArchitectureTest {

  private static final List<Class<?>> CORE_DOMAIN_TYPES =
      List.of(
          DataSourceDefinition.class,
          ConnectionProfile.class,
          DataSourceQuery.class,
          DataSourceSummary.class);

  private static final String[] FORBIDDEN_DEPENDENCIES = {
    ".bean.dto.",
    ".bean.vo.",
    ".bean.po.",
    ".spi.datasource",
    "com.baomidou.mybatisplus",
    "org.springframework"
  };

  @Test
  void coreDomainDoesNotExposeTransportPersistenceOrPluginContracts() {
    for (Class<?> type : CORE_DOMAIN_TYPES) {
      for (Field field : type.getDeclaredFields()) {
        assertTypeAvoids(type, field.getName(), field.getGenericType());
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        for (Type parameter : constructor.getGenericParameterTypes()) {
          assertTypeAvoids(type, "constructor", parameter);
        }
      }
      for (Method method : type.getDeclaredMethods()) {
        assertTypeAvoids(type, method.getName(), method.getGenericReturnType());
        for (Type parameter : method.getGenericParameterTypes()) {
          assertTypeAvoids(type, method.getName(), parameter);
        }
      }
    }
  }

  @Test
  void connectionProfileIsImmutableDomainValueObject() {
    assertThat(ConnectionProfile.class.isRecord()).isTrue();
    assertThat(ConnectionProfile.class.getDeclaredFields())
        .allMatch(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
  }

  @Test
  void dataSourceAggregateOwnsConfigurationAndConnectionStateBehavior() throws Exception {
    assertThat(
            DataSourceDefinition.class.getMethod(
                "updateConfiguration",
                String.class,
                io.yak.ops.common.enums.datasource.DataSourceDbType.class,
                ConnectionProfile.class,
                io.yak.ops.common.enums.datasource.DataSourceEnvironment.class,
                String.class))
        .isNotNull();
    assertThat(DataSourceDefinition.class.getMethod("replaceConnectionProfile", ConnectionProfile.class))
        .isNotNull();
    assertThat(DataSourceDefinition.class.getMethod("markConnected")).isNotNull();
    assertThat(DataSourceDefinition.class.getMethod("markDisconnected")).isNotNull();
    assertThat(DataSourceDefinition.class.getMethod("markConnectionUnknown")).isNotNull();
  }

  private void assertTypeAvoids(Class<?> owner, String member, Type type) {
    String signature = typeName(type);
    for (String forbidden : FORBIDDEN_DEPENDENCIES) {
      assertThat(signature)
          .as("%s.%s must not expose %s", owner.getSimpleName(), member, forbidden)
          .doesNotContain(forbidden);
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
