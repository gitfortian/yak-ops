package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentNode;
import java.util.List;
import java.util.Optional;

/** Durable repository for data-development tree node metadata. */
public interface DevelopmentNodeRepository {

  /** Creates a Project Root in the trusted CurrentProject owned by the persistence adapter. */
  DevelopmentNode insert(
      String name,
      String type,
      Long directoryId,
      boolean configured);

  Optional<DevelopmentNode> findById(Long id);

  List<DevelopmentNode> list();

  /** Lightweight count for overview/read-model callers; adapters should override when possible. */
  default long count() {
    return list().size();
  }

  boolean existsByName(Long directoryId, String name);

  boolean existsInDirectory(Long directoryId);

  boolean updateName(Long id, String name);

  boolean updateConfigured(Long id, boolean configured);

  /** Stores the last editor without forcing all existing repository test doubles to implement it. */
  default boolean updateUpdatedBy(Long id, String updatedBy) {
    return false;
  }

  boolean deleteById(Long id);
}
