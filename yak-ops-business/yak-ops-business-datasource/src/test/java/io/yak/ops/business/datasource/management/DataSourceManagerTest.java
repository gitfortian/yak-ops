package io.yak.ops.business.datasource.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSourceManagerTest {

  @Mock private DataSourceRepository repository;
  @Mock private DataSourceReader reader;
  @Mock private DataSourceConnectionResolver connectionResolver;

  @Test
  void createBuildsAggregateFromNormalizedConnectionProfile() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.PROD,
            "orders",
            "{\"host\":\"127.0.0.1\"}");
    ConnectionProfile profile =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\"}",
            "{\"host\":\"127.0.0.1\"}");
    when(connectionResolver.normalize(DataSourceDbType.MYSQL, command.connectionJson()))
        .thenReturn(profile);
    when(repository.insert(any(DataSourceDefinition.class))).thenReturn(true);

    assertThat(manager().create(command)).isTrue();

    ArgumentCaptor<DataSourceDefinition> captor =
        ArgumentCaptor.forClass(DataSourceDefinition.class);
    verify(repository).insert(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("orders-db");
    assertThat(captor.getValue().getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
  }

  private DataSourceManager manager() {
    return new DataSourceManager(repository, reader, connectionResolver);
  }
}
