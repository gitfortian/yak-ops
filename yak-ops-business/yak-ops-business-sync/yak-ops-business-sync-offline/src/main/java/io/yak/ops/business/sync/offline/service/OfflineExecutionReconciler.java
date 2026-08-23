package io.yak.ops.business.sync.offline.service;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpNodeResponse;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 使用 YAML 固定地址持续对账，并通过 instanceId 识别 Worker 重启。 */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExecutionReconciler {
  private static final Logger LOG = LoggerFactory.getLogger(OfflineExecutionReconciler.class);

  private final OfflineJobExecutionRepository executionRepository;
  private final OfflineJobExecutionService executionService;
  private final LinkUpClient linkUpClient;
  private final OfflineSyncProperties properties;

  @Scheduled(
      initialDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}",
      fixedDelayString = "${yak.sync.offline.control.reconcile-delay-millis:5000}")
  public void reconcile() {
    int limit = Math.max(1, properties.getControl().getScanBatchSize());
    List<OfflineJobExecution> executions = executionRepository.findActiveExecutions(limit);
    LinkUpNodeResponse node = null;
    RuntimeException probeError = null;
    try {
      node = linkUpClient.node();
    } catch (RuntimeException exception) {
      probeError = exception;
    }
    for (OfflineJobExecution execution : executions) {
      reconcileExecution(execution, node, probeError);
    }
    for (OfflineJobExecution execution : executionRepository.findRetryCandidates(LocalDateTime.now(), limit)) {
      retry(execution);
    }
  }

  private void reconcileExecution(
      OfflineJobExecution execution,
      LinkUpNodeResponse node,
      RuntimeException probeError) {
    try {
      if (probeError != null) throw probeError;
      boolean workerChanged = node != null
          && StringUtils.hasText(execution.getWorkerInstanceId())
          && StringUtils.hasText(node.getInstanceId())
          && !execution.getWorkerInstanceId().equals(node.getInstanceId());
      if (workerChanged
          && OfflineExecutionStatus.parse(execution.getStatus()) != OfflineExecutionStatus.UNKNOWN) {
        executionService.markUnknown(execution, "Link-Up instanceId 已变化，旧实例执行结果无法继续确认");
        return;
      }
      LinkUpJobResponse response = StringUtils.hasText(execution.getEngineJobId())
          ? linkUpClient.getJob(execution.getEngineJobId())
          : linkUpClient.findByExternalExecutionId(execution.getExternalExecutionId());
      executionService.applySnapshot(execution, response, "RECONCILED");
      if (Boolean.TRUE.equals(execution.getCancellationRequested())
          && StringUtils.hasText(execution.getEngineJobId())
          && response != null
          && isActive(response.getStatus())) {
        executionService.applySnapshot(
            execution,
            linkUpClient.cancel(execution.getEngineJobId()),
            "CANCEL_RECONCILED");
      }
    } catch (RuntimeException exception) {
      if (isPastUnknownDeadline(execution)) {
        executionService.markUnknown(execution, "Link-Up 状态对账超时：" + exception.getMessage());
      } else {
        LOG.debug("Offline execution reconcile failed, executionId={}", execution.getId(), exception);
      }
    }
  }

  private void retry(OfflineJobExecution execution) {
    try {
      executionService.retryFrom(execution);
    } catch (RuntimeException exception) {
      LOG.warn("Offline execution retry failed, executionId={}", execution.getId(), exception);
    }
  }

  private boolean isPastUnknownDeadline(OfflineJobExecution execution) {
    LocalDateTime reference = execution.getLastSyncTime() == null
        ? execution.getCreateTime()
        : execution.getLastSyncTime();
    return reference != null
        && Duration.between(reference, LocalDateTime.now()).toMillis()
            >= Math.max(1000, properties.getControl().getLostAfterMillis());
  }

  private boolean isActive(String status) {
    return "CREATED".equalsIgnoreCase(status)
        || "SUBMITTED".equalsIgnoreCase(status)
        || "QUEUED".equalsIgnoreCase(status)
        || "RUNNING".equalsIgnoreCase(status);
  }
}
