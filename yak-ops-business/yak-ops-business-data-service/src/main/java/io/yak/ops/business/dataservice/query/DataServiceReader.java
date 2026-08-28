package io.yak.ops.business.dataservice.query;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceReader {

  private final DataServiceRepository repository;

  public DataServiceDefinition require(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("数据服务不存在：" + id));
  }

  public List<DataServiceDefinition> list() {
    return repository.findAll();
  }

  public long count() {
    return repository.count();
  }

  public Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef) {
    return repository.findBySource(sourceType, sourceRef);
  }

  public DataServiceDefinition requireByPath(String path) {
    return repository.findByPath(path)
        .orElseThrow(() -> new IllegalArgumentException("数据服务不存在：" + path));
  }
}
