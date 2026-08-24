package io.yak.ops.business.datasource.controller.v1.mapper;

import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.DataSourceSummary;
import io.yak.ops.business.datasource.gateway.DataSourcePluginGateway;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Domain -> HTTP VO projection; plugin-owned sensitive data is masked through the business gateway. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceViewMapper {

  private final DataSourcePluginGateway pluginGateway;

  public DataSourceVO definition(DataSourceDefinition source, boolean includeOriginalJson) {
    if (source == null) return null;
    DataSourceVO target = new DataSourceVO();
    target.setId(source.getId());
    target.setName(source.getName());
    target.setDbType(source.getDbType() == null ? null : source.getDbType().name());
    target.setJdbcUrl(pluginGateway.maskSensitiveText(source.getJdbcUrl()));
    target.setEnvironment(source.getEnvironment() == null ? null : source.getEnvironment().name());
    target.setEnvironmentName(
        source.getEnvironment() == null ? null : source.getEnvironment().getDisplayName());
    target.setConnStatus(source.getConnStatus() == null ? null : source.getConnStatus().name());
    target.setRemark(source.getRemark());
    target.setCreateTime(source.getCreateTime());
    target.setUpdateTime(source.getUpdateTime());
    if (includeOriginalJson && source.getDbType() != null) {
      target.setOriginalJson(
          pluginGateway.maskConnectionJson(source.getDbType(), source.getOriginalJson()));
    }
    return target;
  }

  public PagingData<DataSourceVO> page(PageData<DataSourceDefinition> page) {
    return PagingData.from(page.map(value -> definition(value, false)));
  }

  public PagingData<DataSourceVO> all(List<DataSourceDefinition> definitions) {
    List<DataSourceVO> records =
        definitions == null
            ? List.of()
            : definitions.stream().map(value -> definition(value, false)).toList();
    long pages = records.isEmpty() ? 0L : 1L;
    long pageSize = Math.max(1, records.size());
    return PagingData.from(new PageData<>(records, records.size(), pages, 1L, pageSize));
  }

  public List<DataSourceOptionVO> options(List<DataSourceDefinition> definitions) {
    if (definitions == null) return List.of();
    return definitions.stream().map(this::option).toList();
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
