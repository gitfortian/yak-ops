package io.yak.ops.business.lineage;

import io.yak.ops.business.lineage.repository.LineageRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maintenance operations for replacing provenance-scoped generated lineage. */
@Service
public class LineageMaintenanceService {

  private final LineageRepository repository;

  public LineageMaintenanceService(LineageRepository repository) {
    this.repository = repository;
  }

  @Transactional("yakBusinessTransactionManager")
  public int clearRelationsByEvidence(String sourceType, String sourceId) {
    String normalizedType = required(sourceType, "sourceType", 64);
    String normalizedId = required(sourceId, "sourceId", 200);
    return repository.deleteRelationsByEvidence(normalizedType, normalizedId);
  }

  /** Starts an evidence-scoped replacement and remembers only its former endpoints. */
  @Transactional("yakBusinessTransactionManager")
  public CleanupScope beginReplacement(
      String sourceType, String sourceId, String ownerType, String ownerId) {
    String evidenceType = required(sourceType, "sourceType", 64);
    String evidenceId = required(sourceId, "sourceId", 200);
    CleanupScope scope = new CleanupScope(
        evidenceType,
        evidenceId,
        required(ownerType, "ownerType", 64),
        required(ownerId, "ownerId", 200),
        repository.findAssetIdsByEvidence(evidenceType, evidenceId));
    repository.deleteRelationsByEvidence(evidenceType, evidenceId);
    return scope;
  }

  /** Deletes old endpoints only when explicit ownership, all edges, and children permit it. */
  @Transactional("yakBusinessTransactionManager")
  public int finishReplacement(CleanupScope scope) {
    if (scope == null) throw new IllegalArgumentException("cleanupScope 不能为空");
    return repository.deleteUnreferencedOwnedAssets(
        scope.candidateAssetIds(), scope.ownerType(), scope.ownerId());
  }

  /** Serializes publishers and rejects an older revision after a newer revision has committed. */
  @Transactional("yakBusinessTransactionManager")
  public boolean lockAndAcceptRevision(String assetKey, int revisionNo) {
    String key = required(assetKey, "assetKey", 512);
    return repository.lockAssetByKey(key).map(asset -> {
      if (asset.properties() == null || !asset.properties().has("revisionNo")) return true;
      return asset.properties().path("revisionNo").asInt(Integer.MIN_VALUE) <= revisionNo;
    }).orElse(true);
  }

  public record CleanupScope(
      String sourceType,
      String sourceId,
      String ownerType,
      String ownerId,
      Set<Long> candidateAssetIds) {

    public CleanupScope {
      candidateAssetIds = candidateAssetIds == null ? Set.of() : Set.copyOf(candidateAssetIds);
    }
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
