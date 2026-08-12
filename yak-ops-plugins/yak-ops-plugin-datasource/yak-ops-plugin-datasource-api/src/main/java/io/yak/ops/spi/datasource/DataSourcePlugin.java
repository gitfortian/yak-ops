package io.yak.ops.spi.datasource;

import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;

/** 数据源插件稳定扩展契约。 */
public interface DataSourcePlugin {

  /** 插件唯一对应的数据源类型。 */
  DataSourceDbType dbType();

  /** 插件负责下发自己的动态表单和默认参数。 */
  DataSourcePluginConfigVO pluginConfig();

  /** 解析、校验并规范化前端连接参数。 */
  DataSourceConnection parseConnection(String connectionJson);

  /** 执行连接测试，失败时抛出 {@link DataSourcePluginException}。 */
  void testConnection(DataSourceConnection connection, int timeoutSeconds);

  /** 创建由插件实现的 Catalog 元数据访问器。 */
  DataSourceCatalog createCatalog(DataSourceConnection connection, int timeoutSeconds);

  /**
   * 创建一次 SQL 物理执行器。
   *
   * <p>默认不支持，JDBC 等具备 SQL 执行能力的插件显式覆盖。Task Plugin 不直接接触 JDBC
   * Connection 或凭据，只通过这个稳定契约获得执行能力。
   */
  default DataSourceSqlExecutor createSqlExecutor(
      DataSourceConnection connection,
      int connectionTimeoutSeconds) {
    throw new DataSourcePluginException(
        DataSourcePluginException.Operation.EXECUTION,
        "当前数据源插件不支持 SQL 执行：" + dbType());
  }

  /** 插件是否理解指定连接地址。 */
  default boolean acceptsUrl(String jdbcUrl) {
    return false;
  }
}
