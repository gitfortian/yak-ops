package io.yak.ops.plugin.database.jdbc.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormFieldVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormSectionVO;
import io.yak.ops.plugin.database.jdbc.JdbcConnectionProperties;
import io.yak.ops.plugin.database.jdbc.SshTunnelConfig;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import org.junit.jupiter.api.Test;

class MySqlDataSourcePluginTest {

  @Test
  void shouldUseMysqlConnectorJDefaults() {
    DataSourceConnection connection =
        new MySqlDataSourcePlugin()
            .parseConnection(
                "{\"dbType\":\"MYSQL\",\"host\":\"127.0.0.1\","
                    + "\"database\":\"demo\",\"username\":\"root\"}");

    assertThat(connection.jdbcUrl()).isEqualTo("jdbc:mysql://127.0.0.1:3306/demo");
    assertThat(connection.driverClassName()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(new MySqlDataSourcePlugin().acceptsUrl(connection.jdbcUrl())).isTrue();
    assertThat(new MySqlDataSourcePlugin().acceptsUrl("jdbc:mariadb://127.0.0.1/demo"))
        .isFalse();
  }

  @Test
  void shouldExposeSectionedFormSchemaAndKeepLegacyFields() {
    DataSourcePluginConfigVO config = new MySqlDataSourcePlugin().pluginConfig();

    assertThat(config.getSections())
        .extracting(FormSectionVO::getKey)
        .containsExactly("connection", "ssh", "driver", "advanced");
    assertThat(config.getSections().get(0).getCollapsible()).isFalse();
    assertThat(config.getSections().get(1).getCollapsible()).isTrue();
    assertThat(config.getSections().get(1).getDefaultExpanded()).isFalse();
    assertThat(config.getSections().get(1).getFields())
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.getKey()).isEqualTo("sshTunnel");
              assertThat(field.getType()).isEqualTo("SSH");
            });
    assertThat(config.getSections().get(2).getDefaultExpanded()).isTrue();
    assertThat(config.getSections().get(3).getDefaultExpanded()).isFalse();

    FormFieldVO propertiesField =
        config.getSections().get(3).getFields().stream()
            .filter(field -> "properties".equals(field.getKey()))
            .findFirst()
            .orElseThrow();
    assertThat(propertiesField.getDependsOn()).containsExactly("driverClassName");
    assertThat(propertiesField.getVisibleWhen())
        .singleElement()
        .satisfies(
            condition -> {
              assertThat(condition.getField()).isNull();
              assertThat(condition.getOperator()).isEqualTo("TRUTHY");
            });

    // 旧版前端仍可继续消费扁平 formFields，SSH 复合字段只通过新版 sections 下发。
    assertThat(config.getFormFields()).hasSize(9);
  }

  @Test
  void shouldParseStandardSshTunnelConfig() {
    JdbcConnectionProperties connection =
        (JdbcConnectionProperties)
            new MySqlDataSourcePlugin()
                .parseConnection(
                    "{\"dbType\":\"MYSQL\",\"host\":\"db.internal\","
                        + "\"port\":3306,\"database\":\"demo\",\"username\":\"root\","
                        + "\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion.example.com\","
                        + "\"port\":22,\"username\":\"ops\",\"authType\":\"PASSWORD\","
                        + "\"password\":\"secret\"}}}");

    assertThat(connection.host()).isEqualTo("db.internal");
    assertThat(connection.port()).isEqualTo(3306);
    assertThat(connection.sshTunnel().enabled()).isTrue();
    assertThat(connection.sshTunnel().host()).isEqualTo("bastion.example.com");
    assertThat(connection.sshTunnel().port()).isEqualTo(22);
    assertThat(connection.sshTunnel().username()).isEqualTo("ops");
    assertThat(connection.sshTunnel().authType())
        .isEqualTo(SshTunnelConfig.AuthType.PASSWORD);
    assertThat(connection.normalizedJson()).contains("\"sshTunnel\"");
  }

  @Test
  void shouldRejectCustomJdbcUrlWhenSshTunnelIsEnabled() {
    assertThatThrownBy(
            () ->
                new MySqlDataSourcePlugin()
                    .parseConnection(
                        "{\"dbType\":\"MYSQL\",\"host\":\"db.internal\","
                            + "\"database\":\"demo\",\"username\":\"root\","
                            + "\"jdbcUrl\":\"jdbc:mysql://db.internal:3306/demo\","
                            + "\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion.example.com\","
                            + "\"username\":\"ops\",\"password\":\"secret\"}}}"))
        .isInstanceOf(DataSourcePluginException.class)
        .hasMessageContaining("启用 SSH 隧道时");
  }
}
