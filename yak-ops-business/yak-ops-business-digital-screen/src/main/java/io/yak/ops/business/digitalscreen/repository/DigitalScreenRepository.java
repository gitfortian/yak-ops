package io.yak.ops.business.digitalscreen.repository;

import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for Digital Screen definitions. */
public interface DigitalScreenRepository {

  List<DigitalScreen> list();

  Optional<DigitalScreen> findById(long id);

  DigitalScreen insert(
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings);

  DigitalScreen update(
      long id,
      String name,
      String description,
      Map<String, Object> bindings);

  DigitalScreen updateStatus(long id, DigitalScreenStatus status, Instant publishedTime);

  boolean deleteById(long id);
}
