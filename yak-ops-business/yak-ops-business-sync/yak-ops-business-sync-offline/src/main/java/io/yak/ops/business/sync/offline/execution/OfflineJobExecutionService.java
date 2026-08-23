package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionLogService;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionReadService;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBatchOperationDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobExecutionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBatchOperationErrorVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBatchOperationVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineEngineHealthVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionEventVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogPageVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionDetailVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 离线同步执行门面；Controller、Schedule、Backfill Dispatcher、Reconciler 统一从这里进入 execution 子系统。 */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineJobExecutionService {

  private final OfflineExecutionOrchestrator orchestrator;
  private final OfflineBatchRuntimeService batchRuntimeService;
  private final OfflineExecutionReadService readService;
  private final OfflineExecutionLogService logService;
  private final LinkUpClient linkUpClient;
  private final OfflineSyncViewMapper viewMapper;

  public OfflineEngineHealthVO health() {
    return viewMapper.engineHealth(linkUpClient.node());
  }

  public boolean hasOccupyingBatch(Long definitionId) {
    return batchRuntimeService.hasOccupyingBatch(definitionId);
  }

  public OfflineJobExecutionVO execute(Long id) {
    return readService.toVO(orchestrator.execute(id, "MANUAL", null, 1));
  }

  /** 工作流按发布时固定的任务版本快照执行。 */
  public OfflineJobExecutionVO executeSnapshot(
      Long id,
      long version,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    return executeSnapshot(
        id,
        version,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        null);
  }

  /** 工作流按发布快照和 Attempt 幂等键执行。 */
  public OfflineJobExecutionVO executeSnapshot(
      Long id,
      long version,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String idempotencyKey) {
    return readService.toVO(
        orchestrator.executeSnapshot(
            id,
            version,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson,
            idempotencyKey));
  }

  public OfflineJobExecutionVO executeScheduled(Long id) {
    return executeScheduled(id, "SCHEDULE");
  }

  /** Schedule Handler 保留完整 trigger token，通过 Facade 进入 execution 子系统。 */
  public OfflineJobExecutionVO executeScheduled(Long id, String triggerToken) {
    return readService.toVO(orchestrator.execute(id, triggerToken, null, 1));
  }

  /** Backfill Dispatcher 只负责触发，不直接调用内部 Orchestrator。 */
  public OfflineJobExecutionVO executePendingBackfill(Long batchId) {
    return readService.toVO(orchestrator.executePendingBackfill(batchId));
  }

  public OfflineJobExecutionVO retry(Long id) {
    return readService.toVO(orchestrator.retryFrom(readService.require(id)));
  }

  public OfflineJobExecutionVO retryFrom(OfflineJobExecution previous) {
    return readService.toVO(orchestrator.retryFrom(previous));
  }

  public OfflineJobExecutionVO cancel(Long id) {
    return readService.toVO(orchestrator.cancel(id));
  }

  /** Task 级停止只从 BatchExecution/latest Attempt 选择目标，不读取 Task.lastExecutionId。 */
  public OfflineJobExecutionVO cancelLatest(Long definitionId) {
    return readService.toVO(orchestrator.cancelLatestBatch(definitionId));
  }

  public OfflineBatchOperationVO batchExecute(OfflineBatchOperationDTO request) {
    return batch(request, true);
  }

  public OfflineBatchOperationVO batchCancel(OfflineBatchOperationDTO request) {
    return batch(request, false);
  }

  public PagingData<OfflineJobExecutionVO> page(OfflineJobExecutionQueryDTO query) {
    return readService.page(query);
  }

  public OfflineJobExecutionDetailVO detail(Long id) {
    return readService.detail(id);
  }

  public JsonNode tableMetrics(Long id) {
    return readService.tableMetrics(id);
  }

  public List<OfflineExecutionEventVO> events(Long id) {
    return readService.events(id);
  }

  /** 旧文本接口保留，并直接渲染新的统一时间线。 */
  public String logs(Long id) {
    return logService.text(readService.require(id));
  }

  public OfflineExecutionLogPageVO logs(Long id, String cursor, int limit) {
    return logService.logs(readService.require(id), cursor, limit);
  }

  /** Reconciler 的状态同步入口；内部状态迁移仍由 Orchestrator 负责。 */
  public void applySnapshot(OfflineJobExecution execution, LinkUpJobResponse response, String type) {
    orchestrator.applySnapshot(execution, response, type);
  }

  /** Reconciler 的 UNKNOWN 收口入口。 */
  public void markUnknown(OfflineJobExecution execution, String message) {
    orchestrator.markUnknown(execution, message);
  }

  private OfflineBatchOperationVO batch(OfflineBatchOperationDTO request, boolean execute) {
    if (request == null
        || request.getJobDefinitionIds() == null
        || request.getJobDefinitionIds().isEmpty()) {
      throw new IllegalArgumentException("jobDefinitionIds 不能为空");
    }

    int success = 0;
    List<OfflineBatchOperationErrorVO> errors = new ArrayList<>();
    for (Long id : request.getJobDefinitionIds()) {
      try {
        if (id == null || id <= 0L) throw new IllegalArgumentException("任务定义 ID 不合法");
        if (execute) execute(id); else cancelLatest(id);
        success++;
      } catch (RuntimeException exception) {
        errors.add(
            OfflineBatchOperationErrorVO.builder()
                .jobDefinitionId(id)
                .message(exception.getMessage())
                .build());
      }
    }

    return OfflineBatchOperationVO.builder()
        .successCount(success)
        .failedCount(errors.size())
        .errors(errors)
        .build();
  }
}
