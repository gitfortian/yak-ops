package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Normalizes datasource application inputs and maps enum parsing failures to business errors. */
@Component
@ConditionalOnDataSourceEnabled
public class DataSourceValidator {

  public String normalizeName(String name) {
    if (!StringUtils.hasText(name)) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据源名称不能为空");
    }
    return name.trim();
  }

  public String normalizeNullable(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  public DataSourceDbType parseDbType(String value) {
    try {
      return DataSourceDbType.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE,
          exception.getMessage(),
          exception);
    }
  }

  public DataSourceEnvironment parseEnvironment(String value) {
    try {
      return DataSourceEnvironment.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_ENVIRONMENT,
          exception.getMessage(),
          exception);
    }
  }

  public DataSourceConnStatus parseConnectionStatus(String value) {
    try {
      return DataSourceConnStatus.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_STATUS,
          "不支持的连接状态：" + value,
          exception);
    }
  }

  public long requireId(Long id) {
    if (id == null || id <= 0L) {
      throw new DataSourceException(DataSourceErrorCode.NOT_FOUND);
    }
    return id;
  }
}
