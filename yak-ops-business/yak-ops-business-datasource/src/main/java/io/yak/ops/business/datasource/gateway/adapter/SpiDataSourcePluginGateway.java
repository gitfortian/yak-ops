package io.yak.ops.business.datasource.gateway.adapter;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.util.DataSourceSecretCodec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.spi.datasource.DataSourceConnection;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import io.yak.ops.spi.datasource.DataSourcePluginException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Datasource Plugin SPI -> Business Gateway Adapter。 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SpiDataSourcePluginGateway implements DataSourcePluginGateway {

  private final DataSourcePluginRegistry pluginRegistry;
  private final DataSourceSecretCodec secretCodec;

  @Override
  public DataSourceDbType resolveConnectionType(String connectionJson) {
    return pluginRegistry.resolveConnectionType(connectionJson);
  }

  @Override
  public ConnectionProfile normalizeConnection(
      DataSourceDbType dbType,
      String connectionJson) {
    DataSourceConnection connection = parseConnection(pluginRegistry.get(dbType), connectionJson);
    return new ConnectionProfile(
        connection.jdbcUrl(),
        connection.normalizedJson(),
        connection.normalizedJson());
  }

  @Override
  public String mergeStoredSecrets(
      DataSourceDbType dbType,
      String submittedJson,
      String storedJson) {
    return secretCodec.mergeStoredSecrets(
        pluginRegistry.get(dbType),
        submittedJson,
        storedJson);
  }

  @Override
  public void testConnection(
      DataSourceDbType dbType,
      ConnectionProfile connectionProfile,
      int timeoutSeconds) {
    if (connectionProfile == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据源连接配置不能为空");
    }
    DataSourcePlugin plugin = pluginRegistry.get(dbType);
    DataSourceConnection connection =
        parseConnection(plugin, connectionProfile.normalizedJson());
    try {
      plugin.testConnection(connection, Math.max(1, timeoutSeconds));
    } catch (DataSourceException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.CONNECT_FAILED,
          exception.getMessage(),
          exception);
    }
  }

  @Override
  public String maskConnectionJson(
      DataSourceDbType dbType,
      String connectionJson) {
    return secretCodec.maskConnectionJson(pluginRegistry.get(dbType), connectionJson);
  }

  @Override
  public String maskSensitiveText(String value) {
    return secretCodec.maskSensitiveText(value);
  }

  private DataSourceConnection parseConnection(
      DataSourcePlugin plugin,
      String connectionJson) {
    try {
      return plugin.parseConnection(connectionJson);
    } catch (DataSourcePluginException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          exception.getMessage(),
          exception);
    } catch (RuntimeException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          exception.getMessage(),
          exception);
    }
  }
}
