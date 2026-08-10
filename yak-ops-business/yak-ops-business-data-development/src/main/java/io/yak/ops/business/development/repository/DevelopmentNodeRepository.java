package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentNode;
import java.util.List;
import java.util.Optional;

/** Durable repository for data-development tree node metadata. */
public interface DevelopmentNodeRepository {

  DevelopmentNode insert(
      String name,
      String type,
      Long projectId,
      Long directoryId,
      boolean configured);

  Optional<DevelopmentNode> findById(Long id);

  List<DevelopmentNode> list();

  boolean existsByName(Long directoryId, String name);
}
