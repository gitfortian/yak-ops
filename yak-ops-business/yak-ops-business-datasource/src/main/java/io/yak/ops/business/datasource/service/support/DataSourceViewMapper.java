package io.yak.ops.business.datasource.service.support;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.plugin.DataSourcePluginRegistry;
import io.yak.ops.business.datasource.util.DataSourceSecretCodec;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.spi.datasource.DataSourcePlugin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Domain -> VO 纯输出转换，不访问数据库。 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceViewMapper {

  private final DataSourcePluginRegistry pluginRegistry;
  private final DataSourceSecretCodec secretCodec;

  public DataSourceVO definition(DataSourceDefinition source, boolean includeOriginalJson) {
    if (source == null) return null;
    DataSourceVO target = new DataSourceVO();
    target.setId(source.getId());
    target.setName(source.getName());
    target.setDbType(source.getDbType() == null ? null : source.getDbType().name());
    target.setJdbcUrl(secretCodec.maskSensitiveText(source.getJdbcUrl()));
    target.setEnvironment(source.getEnvironment() == null ? null : source.getEnvironment().name());
    target.setEnvironmentName(
        source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
    target.setConnStatus(source.getConnStatus() == null ? null : source.getConnStatus().name());
    target.setRemark(source.getRemark());
    target.setCreateTime(source.getCreateTime());
    target.setUpdateTime(source.getUpdateTime());
    if (includeOriginalJson && source.getDbType() != null) {
      DataSourcePlugin plugin = pluginRegistry.get(source.getDbType());
      target.setOriginalJson(secretCodec.maskConnectionJson(plugin, source.getOriginalJson()));
    }
    return target;
  }

  public DataSourceOptionVO option(DataSourceDefinition source) {
    return new DataSourceOptionVO(
        source.getName(),
        String.valueOf(source.getId()),
        source.getDbType() == null ? null : source.getDbType().name());
  }

  public DataSourceSummaryVO summary(DataSourceSummary source) {
    DataSourceSummary value = source == null ? DataSourceSummary.empty() : source;
    return new DataSourceSummaryVO(
        value.total(),
        value.connected(),
        value.disconnected(),
        value.unknown(),
        value.environmentCount());
  }
}
