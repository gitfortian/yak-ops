package io.yak.ops.business.datasource.gateway;

import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.common.enums.datasource.DataSourceDbType;

/**
 * Business Datasource 对插件连接能力的稳定 Port。
 *
 * <p>Application / Interface 层只依赖该接口，不直接认识 Datasource Plugin SPI 的 Plugin、Connection
 * 或异常类型；SPI 适配、Secret 处理和异常映射由 Adapter 负责。
 */
public interface DataSourcePluginGateway {

  /** 从未保存连接 JSON 中识别目标数据源类型。 */
  DataSourceDbType resolveConnectionType(String connectionJson);

  /** 解析、校验并规范化连接参数为 Business Domain 的 ConnectionProfile。 */
  ConnectionProfile normalizeConnection(DataSourceDbType dbType, String connectionJson);

  /** 编辑/测试已有数据源时，将掩码或缺失 Secret 与已保存配置合并。 */
  String mergeStoredSecrets(
      DataSourceDbType dbType,
      String submittedJson,
      String storedJson);

  /** 测试一个已经规范化的连接配置。失败时抛出 Business Datasource 异常。 */
  void testConnection(
      DataSourceDbType dbType,
      ConnectionProfile connectionProfile,
      int timeoutSeconds);

  /** 返回仅用于 HTTP 回显的脱敏连接 JSON。 */
  String maskConnectionJson(DataSourceDbType dbType, String connectionJson);

  /** 对展示用连接地址中的常见凭据参数做兜底脱敏。 */
  String maskSensitiveText(String value);
}
