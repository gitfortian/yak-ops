package io.yak.ops.business.lineage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance operations for replacing provenance-scoped generated lineage. */
@Service
public class LineageMaintenanceService {

  private final LineageRepository repository;

  LineageMaintenanceService(LineageRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public int clearRelationsByEvidence(String sourceType, String sourceId) {
    String normalizedType = required(sourceType, "sourceType", 64);
    String normalizedId = required(sourceId, "sourceId", 200);
    return repository.deleteRelationsByEvidence(normalizedType, normalizedId);
  }

  private static String required(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " 不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(field + " 长度不能超过 " + maxLength);
    }
    return normalized;
  }
}
