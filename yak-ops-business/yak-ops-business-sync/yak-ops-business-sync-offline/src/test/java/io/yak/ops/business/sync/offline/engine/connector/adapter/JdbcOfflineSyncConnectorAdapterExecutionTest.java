package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import org.junit.jupiter.api.Test;

class JdbcOfflineSyncConnectorAdapterExecutionTest {

  @Test
  void injectsOceanBaseCompatibilityAndDatasourcePropertiesOnlyAtExecutionTime() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(10L);
    dataSource.setName("oceanbase");
    dataSource.setDbType(DataSourceDbType.OCEANBASE);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:oceanbase://127.0.0.1:2881/app\","
            + "\"driverClassName\":\"com.oceanbase.jdbc.Driver\","
            + "\"username\":\"root\",\"password\":\"secret\","
            + "\"compatibleMode\":\"oracle\","
            + "\"properties\":{\"connectTimeout\":\"30000\"}}");

    ObjectNode options = mapper.createObjectNode();
    options.put("table_path", "APP.ORDERS");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SOURCE, "来源端", dataSource, options));

    assertThat(options.path("url").asText())
        .isEqualTo("jdbc:oceanbase://127.0.0.1:2881/app");
    assertThat(options.path("driver").asText()).isEqualTo("com.oceanbase.jdbc.Driver");
    assertThat(options.path("compatible_mode").asText()).isEqualTo("oracle");
    assertThat(options.path("properties").path("connectTimeout").asText()).isEqualTo("30000");
    assertThat(options.path("password").asText()).isEqualTo("secret");
  }
}
