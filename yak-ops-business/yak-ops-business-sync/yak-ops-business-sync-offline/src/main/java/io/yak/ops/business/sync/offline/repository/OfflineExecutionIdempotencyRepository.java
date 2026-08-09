package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @deprecated 兼容旧内部调用；新代码直接使用 OfflineJobExecutionRepository。
 */
@Deprecated(forRemoval = true)
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExecutionIdempotencyRepository {
  private final OfflineJobExecutionRepository executionRepository;

  public Optional<OfflineJobExecution> findByKey(String idempotencyKey) {
    return executionRepository.findByIdempotencyKey(idempotencyKey);
  }
}
