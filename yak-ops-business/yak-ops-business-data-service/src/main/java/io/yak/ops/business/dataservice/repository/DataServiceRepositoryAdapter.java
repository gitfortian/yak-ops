package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceApiMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceApiPO;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceRepositoryAdapter implements DataServiceRepository {

  private final DataServiceApiMapper mapper;

  @Override
  public Optional<DataServiceDefinition> findById(Long id) {
    return Optional.ofNullable(id == null ? null : mapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public Optional<DataServiceDefinition> findByPath(String path) {
    if (path == null) return Optional.empty();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getPath, path)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef) {
    if (sourceType == null || sourceRef == null) return Optional.empty();
    return Optional.ofNullable(mapper.selectOne(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .eq(DataServiceApiPO::getSourceType, sourceType)
                .eq(DataServiceApiPO::getSourceRef, sourceRef)
                .last("LIMIT 1")))
        .map(this::toDomain);
  }

  @Override
  public List<DataServiceDefinition> findAll() {
    return mapper.selectList(
            Wrappers.<DataServiceApiPO>lambdaQuery()
                .orderByDesc(DataServiceApiPO::getUpdateTime)
                .orderByDesc(DataServiceApiPO::getId))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public long count() {
    Long count = mapper.selectCount(Wrappers.<DataServiceApiPO>lambdaQuery());
    return count == null ? 0L : count;
  }

  @Override
  public boolean existsByPath(String path, Long excludeId) {
    Long count = mapper.selectCount(
        Wrappers.<DataServiceApiPO>lambdaQuery()
            .eq(DataServiceApiPO::getPath, path)
            .ne(excludeId != null, DataServiceApiPO::getId, excludeId));
    return count != null && count > 0L;
  }

  @Override
  public DataServiceDefinition save(DataServiceDefinition definition) {
    DataServiceApiPO po = toPo(definition);
    if (definition.id() == null) mapper.insert(po);
    else mapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public boolean delete(Long id) {
    return id != null && mapper.deleteById(id) > 0;
  }

  private DataServiceDefinition toDomain(DataServiceApiPO po) {
    DataServiceSettings settings = new DataServiceSettings(
        po.getName(), po.getPath(), value(po.getMaxRows(), 1_000), value(po.getTimeoutSeconds(), 30),
        Boolean.TRUE.equals(po.getEnabled()), po.getDescription(), Boolean.TRUE.equals(po.getPaginationEnabled()));
    PublishedRuntimeSnapshot runtime = new PublishedRuntimeSnapshot(po.getDataSourceId(), po.getSqlText());
    SourceReference source = new SourceReference(
        po.getSourceType(), po.getSourceRef(), po.getSourceRevisionId(), po.getSourceRevisionNo());
    RuntimePolicy policy = new RuntimePolicy(
        Boolean.TRUE.equals(po.getCacheEnabled()), value(po.getCacheTtlSeconds(), 60),
        value(po.getCacheMaxEntries(), 200), Boolean.TRUE.equals(po.getCircuitBreakerEnabled()),
        value(po.getCircuitFailureThreshold(), 5), value(po.getCircuitRecoverySeconds(), 30));
    return DataServiceDefinition.restore(
        po.getId(), settings, runtime, source, policy, AuthMode.parse(po.getAuthMode()),
        po.getCreateTime(), po.getUpdateTime());
  }

  private DataServiceApiPO toPo(DataServiceDefinition definition) {
    DataServiceApiPO po = new DataServiceApiPO();
    po.setId(definition.id());
    DataServiceSettings settings = definition.settings();
    po.setName(settings.name());
    po.setPath(settings.path());
    po.setMaxRows(settings.maxRows());
    po.setTimeoutSeconds(settings.timeoutSeconds());
    po.setEnabled(settings.enabled());
    po.setDescription(settings.description());
    po.setPaginationEnabled(settings.paginationEnabled());
    PublishedRuntimeSnapshot runtime = definition.runtimeSnapshot();
    po.setDataSourceId(runtime.dataSourceId());
    po.setSqlText(runtime.sql());
    po.setAuthMode(definition.authMode().name());
    RuntimePolicy policy = definition.runtimePolicy();
    po.setCacheEnabled(policy.cacheEnabled());
    po.setCacheTtlSeconds(policy.cacheTtlSeconds());
    po.setCacheMaxEntries(policy.cacheMaxEntries());
    po.setCircuitBreakerEnabled(policy.circuitBreakerEnabled());
    po.setCircuitFailureThreshold(policy.failureThreshold());
    po.setCircuitRecoverySeconds(policy.recoverySeconds());
    SourceReference source = definition.sourceReference();
    po.setSourceType(source.sourceType());
    po.setSourceRef(source.sourceRef());
    po.setSourceRevisionId(source.sourceRevisionId());
    po.setSourceRevisionNo(source.sourceRevisionNo());
    po.setCreateTime(definition.createTime());
    po.setUpdateTime(definition.updateTime());
    return po;
  }

  private int value(Integer value, int fallback) {
    return value == null || value <= 0 ? fallback : value;
  }
}
