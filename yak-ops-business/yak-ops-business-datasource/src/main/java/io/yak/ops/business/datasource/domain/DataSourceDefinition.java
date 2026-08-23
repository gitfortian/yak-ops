package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Data;
import lombok.ToString;

/**
 * 数据源聚合根；当前保留 {@code DataSourceDefinition} 历史命名以维持兼容。
 *
 * <p>持久化层暂时仍以 jdbcUrl / connectionParams / originalJson 三个标量字段存储连接信息；业务修改必须把它们作为
 * {@link ConnectionProfile} 整体处理，避免部分更新和旧连接状态漂移。
 */
@Data
public class DataSourceDefinition {
  private Long id;
  private String name;
  private DataSourceDbType dbType;
  private String jdbcUrl;
  private DataSourceEnvironment environment;
  private DataSourceConnStatus connStatus;
  private String remark;
  @ToString.Exclude private String connectionParams;
  @ToString.Exclude private String originalJson;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** 创建新的数据源聚合。创建只代表配置有效，连接状态必须从 UNKNOWN 开始。 */
  public static DataSourceDefinition create(
      String name,
      DataSourceDbType dbType,
      ConnectionProfile connectionProfile,
      DataSourceEnvironment environment,
      String remark) {
    DataSourceDefinition definition = new DataSourceDefinition();
    definition.name = requireText(name, "数据源名称不能为空");
    definition.dbType = Objects.requireNonNull(dbType, "数据源类型不能为空");
    definition.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
    definition.remark = normalizeNullable(remark);
    definition.replaceConnectionProfile(connectionProfile);
    return definition;
  }

  /**
   * 修改数据源可编辑配置。
   *
   * <p>数据源类型创建后不可修改；连接配置发生变化后，旧连接测试结果失效并回到 UNKNOWN。
   */
  public void updateConfiguration(
      String name,
      DataSourceDbType requestedType,
      ConnectionProfile connectionProfile,
      DataSourceEnvironment environment,
      String remark) {
    assertTypeUnchanged(requestedType);
    this.name = requireText(name, "数据源名称不能为空");
    this.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
    this.remark = normalizeNullable(remark);
    replaceConnectionProfile(connectionProfile);
  }

  /** 将当前连接配置作为一个完整值对象读取。 */
  public ConnectionProfile connectionProfile() {
    return new ConnectionProfile(jdbcUrl, connectionParams, originalJson);
  }

  /** 整体替换连接配置，并使旧连接测试结果失效。 */
  public void replaceConnectionProfile(ConnectionProfile connectionProfile) {
    ConnectionProfile profile =
        Objects.requireNonNull(connectionProfile, "数据源连接配置不能为空");
    this.jdbcUrl = profile.jdbcUrl();
    this.connectionParams = profile.normalizedJson();
    this.originalJson = profile.originalJson();
    markConnectionUnknown();
  }

  /** 校验编辑请求没有修改数据源类型。 */
  public void assertTypeUnchanged(DataSourceDbType requestedType) {
    DataSourceDbType target = Objects.requireNonNull(requestedType, "数据源类型不能为空");
    if (dbType != null && dbType != target) {
      throw new IllegalArgumentException("编辑数据源时不允许修改数据源类型");
    }
  }

  /** 最近一次针对当前已保存配置的连接测试成功。 */
  public void markConnected() {
    connStatus = DataSourceConnStatus.CONNECTED;
  }

  /** 最近一次针对当前已保存配置的连接测试失败。 */
  public void markDisconnected() {
    connStatus = DataSourceConnStatus.DISCONNECTED;
  }

  /** 当前连接配置尚未被验证，或原有验证结果已因配置修改失效。 */
  public void markConnectionUnknown() {
    connStatus = DataSourceConnStatus.UNKNOWN;
  }

  private static String requireText(String value, String message) {
    String normalized = normalizeNullable(value);
    if (normalized == null) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  private static String normalizeNullable(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
