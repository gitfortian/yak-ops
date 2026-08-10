package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentDirectory;
import java.util.List;
import java.util.Optional;

/** Durable directory repository for data-development organization metadata. */
public interface DevelopmentDirectoryRepository {

  DevelopmentDirectory insert(Long projectId, Long parentId, String name);

  Optional<DevelopmentDirectory> findById(Long id);

  List<DevelopmentDirectory> listByProjectId(Long projectId);

  boolean existsByName(Long projectId, Long parentId, String name);
}
