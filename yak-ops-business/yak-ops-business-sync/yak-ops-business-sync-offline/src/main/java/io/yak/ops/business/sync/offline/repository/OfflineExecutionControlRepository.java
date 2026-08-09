package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @deprecated 兼容旧内部调用；新代码直接依赖 Definition/Execution/Event Repository。
 */
@Deprecated(forRemoval = true)
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExecutionControlRepository {
  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineExecutionEventRepository eventRepository;

  public void lockDefinition(Long id) {
    definitionRepository.lock(id);
  }

  public boolean hasActiveExecution(Long id) {
    return executionRepository.hasActiveExecution(id);
  }

  public List<OfflineJobExecution> findActiveExecutions(int limit) {
    return executionRepository.findActiveExecutions(limit);
  }

  public List<OfflineJobExecution> findRetryCandidates(LocalDateTime now, int limit) {
    return executionRepository.findRetryCandidates(now, limit);
  }

  public void markRetryCreated(Long id) {
    executionRepository.markRetryCreated(id);
  }

  public void recordExecutionEvent(
      Long executionId,
      long version,
      String from,
      String to,
      String type,
      String message,
      String payload) {
    eventRepository.append(
        OfflineExecutionEvent.builder()
            .executionId(executionId)
            .stateVersion(version)
            .fromStatus(from)
            .toStatus(to)
            .eventType(type)
            .message(message)
            .payloadJson(payload)
            .createTime(LocalDateTime.now())
            .build());
  }

  public List<OfflineExecutionEvent> listExecutionEvents(Long id) {
    return eventRepository.list(id);
  }

  public List<OfflineExecutionEvent> listExecutionEventsAfter(Long id, long afterId, int limit) {
    return eventRepository.listAfter(id, afterId, limit);
  }
}
