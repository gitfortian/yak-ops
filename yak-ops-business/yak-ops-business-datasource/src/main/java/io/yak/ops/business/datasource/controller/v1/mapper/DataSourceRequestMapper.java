package io.yak.ops.business.datasource.controller.v1.mapper;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.connection.DataSourceConnectionRequest;
import io.yak.ops.business.datasource.domain.DataSourceQuery;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.management.DataSourceConfigurationCommand;
import io.yak.ops.business.datasource.management.DataSourceValidator;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Maps transport datasource inputs into typed application/domain inputs. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceRequestMapper {

  private final DataSourceValidator validator;

  public DataSourceConfigurationCommand configuration(DataSourceDTO dto) {
    if (dto == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "数据源参数不能为空");
    }
    return new DataSourceConfigurationCommand(
        validator.normalizeName(dto.getName()),
        validator.parseDbType(dto.getDbType()),
        validator.parseEnvironment(dto.getEnvironment()),
        validator.normalizeNullable(dto.getRemark()),
        dto.getConnectionParams());
  }

  public DataSourceQuery query(DataSourceQueryDTO dto) {
    if (dto == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "分页查询参数不能为空");
    }
    return new DataSourceQuery(
        dto.getPageNo(),
        dto.getPageSize(),
        validator.normalizeNullable(dto.getName()),
        validator.normalizeNullable(dto.getKeyword()),
        StringUtils.hasText(dto.getDbType()) ? validator.parseDbType(dto.getDbType()) : null,
        StringUtils.hasText(dto.getEnvironment())
            ? validator.parseEnvironment(dto.getEnvironment())
            : null,
        StringUtils.hasText(dto.getConnStatus())
            ? validator.parseConnectionStatus(dto.getConnStatus())
            : null);
  }

  public DataSourceDbType optionalDbType(String value) {
    return StringUtils.hasText(value) ? validator.parseDbType(value) : null;
  }

  public DataSourceConnectionRequest connectionTest(DataSourceConnectTestDTO dto) {
    if (dto == null) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_CONNECTION_PARAMS,
          "连接测试参数不能为空");
    }
    return new DataSourceConnectionRequest(
        dto.getDataSourceId(),
        StringUtils.hasText(dto.getDbType()) ? validator.parseDbType(dto.getDbType()) : null,
        dto.getConnJson());
  }
}
