package io.yak.ops.business.dataservice.management;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceManager {

  private final DataServiceRepository repository;
  private final DataServiceReader reader;

  @Transactional
  public DataServiceDefinition savePublished(
      DataServiceDefinition existing,
      DataServiceSettings settings,
      PublishedRuntimeSnapshot runtime,
      SourceReference source) {
    ensurePathAvailable(existing == null ? null : existing.id(), settings.path());
    LocalDateTime now = LocalDateTime.now();
    DataServiceDefinition definition;
    if (existing == null) {
      definition = DataServiceDefinition.create(settings, runtime, source, RuntimePolicy.defaults(true), now);
    } else {
      definition = existing;
      definition.republish(settings, runtime, source, now);
    }
    return repository.save(definition);
  }

  @Transactional
  public DataServiceDefinition updateSettings(Long id, DataServiceSettings settings) {
    DataServiceDefinition definition = reader.require(id);
    ensurePathAvailable(id, settings.path());
    definition.updateSettings(settings, LocalDateTime.now());
    return repository.save(definition);
  }

  @Transactional
  public DataServiceDefinition setEnabled(Long id, boolean enabled) {
    DataServiceDefinition definition = reader.require(id);
    definition.setEnabled(enabled, LocalDateTime.now());
    return repository.save(definition);
  }

  @Transactional
  public boolean delete(Long id) {
    reader.require(id);
    return repository.delete(id);
  }

  private void ensurePathAvailable(Long id, String path) {
    if (repository.existsByPath(path, id)) {
      throw new IllegalArgumentException("服务路径已存在：" + path);
    }
  }
}
