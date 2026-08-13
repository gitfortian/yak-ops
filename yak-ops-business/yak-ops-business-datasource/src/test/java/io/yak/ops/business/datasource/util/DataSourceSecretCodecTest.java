package io.yak.ops.business.datasource.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormFieldVO;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceSecretCodecTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DataSourceSecretCodec codec = new DataSourceSecretCodec(objectMapper);

  @Test
  void shouldMaskPasswordInResponseJsonAndJdbcUrl() throws Exception {
    DataSourcePlugin plugin = pluginWithPasswordField();

    String masked =
        codec.maskConnectionJson(
            plugin,
            "{\"host\":\"db\",\"username\":\"root\","
                + "\"password\":\"top-secret\","
                + "\"properties\":{\"accessToken\":\"nested-secret\"}}"
        );
    JsonNode root = objectMapper.readTree(masked);

    assertThat(root.get("password").asText()).isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(root.path("properties").path("accessToken").asText())
        .isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(root.get("username").asText()).isEqualTo("root");
    assertThat(
            codec.maskSensitiveText(
                "jdbc:mysql://root:top-secret@db/demo?password=another-secret"))
        .isEqualTo("jdbc:mysql://root:******@db/demo?password=******");
  }

  @Test
  void shouldMaskNestedSshCredentials() throws Exception {
    DataSourcePlugin plugin = pluginWithPasswordField();

    String masked =
        codec.maskConnectionJson(
            plugin,
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"bastion\","
                + "\"password\":\"ssh-password\",\"privateKey\":\"private-key\","
                + "\"passphrase\":\"key-passphrase\"}}"
        );
    JsonNode ssh = objectMapper.readTree(masked).path("sshTunnel");

    assertThat(ssh.path("password").asText())
        .isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("privateKey").asText())
        .isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("passphrase").asText())
        .isEqualTo(DataSourceSecretCodec.MASKED_VALUE);
    assertThat(ssh.path("host").asText()).isEqualTo("bastion");
  }

  @Test
  void shouldReuseStoredPasswordWhenEditSubmitsMask() throws Exception {
    DataSourcePlugin plugin = pluginWithPasswordField();

    String merged =
        codec.mergeStoredSecrets(
            plugin,
            "{\"host\":\"new-db\",\"password\":\"******\","
                + "\"properties\":{\"accessToken\":\"******\"}}",
            "{\"host\":\"old-db\",\"password\":\"top-secret\","
                + "\"properties\":{\"accessToken\":\"nested-secret\"}}"
        );
    JsonNode root = objectMapper.readTree(merged);

    assertThat(root.get("host").asText()).isEqualTo("new-db");
    assertThat(root.get("password").asText()).isEqualTo("top-secret");
    assertThat(root.path("properties").path("accessToken").asText())
        .isEqualTo("nested-secret");
  }

  @Test
  void shouldReuseStoredSshSecretsWhenEditSubmitsMask() throws Exception {
    DataSourcePlugin plugin = pluginWithPasswordField();

    String merged =
        codec.mergeStoredSecrets(
            plugin,
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"new-bastion\","
                + "\"password\":\"******\",\"privateKey\":\"******\","
                + "\"passphrase\":\"******\"}}",
            "{\"sshTunnel\":{\"enabled\":true,\"host\":\"old-bastion\","
                + "\"password\":\"ssh-password\",\"privateKey\":\"private-key\","
                + "\"passphrase\":\"key-passphrase\"}}"
        );
    JsonNode ssh = objectMapper.readTree(merged).path("sshTunnel");

    assertThat(ssh.path("host").asText()).isEqualTo("new-bastion");
    assertThat(ssh.path("password").asText()).isEqualTo("ssh-password");
    assertThat(ssh.path("privateKey").asText()).isEqualTo("private-key");
    assertThat(ssh.path("passphrase").asText()).isEqualTo("key-passphrase");
  }

  @Test
  void shouldUseNewPasswordWhenUserChangesIt() throws Exception {
    DataSourcePlugin plugin = pluginWithPasswordField();

    String merged =
        codec.mergeStoredSecrets(
            plugin,
            "{\"password\":\"new-secret\"}",
            "{\"password\":\"old-secret\"}");

    assertThat(objectMapper.readTree(merged).get("password").asText())
        .isEqualTo("new-secret");
  }

  private DataSourcePlugin pluginWithPasswordField() {
    DataSourcePlugin plugin = mock(DataSourcePlugin.class);
    DataSourcePluginConfigVO config =
        DataSourcePluginConfigVO.builder()
            .formFields(
                List.of(
                    FormFieldVO.builder().key("username").type("INPUT").build(),
                    FormFieldVO.builder().key("password").type("PASSWORD").build()))
            .build();
    when(plugin.pluginConfig()).thenReturn(config);
    return plugin;
  }
}
