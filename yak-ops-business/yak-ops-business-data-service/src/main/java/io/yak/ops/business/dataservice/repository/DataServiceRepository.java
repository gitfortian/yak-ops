package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import java.util.List;
import java.util.Optional;

public interface DataServiceRepository {

  Optional<DataServiceDefinition> findById(Long id);

  Optional<DataServiceDefinition> findByPath(String path);

  Optional<DataServiceDefinition> findBySource(String sourceType, String sourceRef);

  List<DataServiceDefinition> findAll();

  /** Lightweight overview count; adapters should override to avoid materializing definitions. */
  default long count() {
    return findAll().size();
  }

  boolean existsByPath(String path, Long excludeId);

  DataServiceDefinition save(DataServiceDefinition definition);

  boolean delete(Long id);
}
