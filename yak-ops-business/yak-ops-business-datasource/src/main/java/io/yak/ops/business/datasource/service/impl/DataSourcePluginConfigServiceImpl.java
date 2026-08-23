package io.yak.ops.business.datasource.service.impl;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.business.datasource.service.DataSourcePluginConfigService;
import io.yak.ops.business.datasource.service.support.DataSourcePluginViewMapper;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Datasource plugin configuration service backed by the Business plugin descriptor boundary. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourcePluginConfigServiceImpl implements DataSourcePluginConfigService {

  private final DataSourcePluginGateway pluginGateway;
  private final DataSourcePluginViewMapper viewMapper;

  @Override
  public DataSourcePluginConfigVO getPluginConfig(String pluginType) {
    DataSourceDbType dbType = parseDbType(pluginType);
    return viewMapper.config(pluginGateway.descriptor(dbType));
  }

  @Override
  public boolean installPlugin(String pluginType) {
    pluginGateway.descriptor(parseDbType(pluginType));
    return true;
  }

  private DataSourceDbType parseDbType(String value) {
    try {
      return DataSourceDbType.parse(value);
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE, exception.getMessage(), exception);
    }
  }
}
