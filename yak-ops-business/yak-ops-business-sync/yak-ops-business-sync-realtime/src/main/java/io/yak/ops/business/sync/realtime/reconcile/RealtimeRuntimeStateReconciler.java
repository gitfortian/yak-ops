package io.yak.ops.business.sync.realtime.reconcile;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns convergence of local SyncExecution state with observed external runtime state. */
@Component
public class RealtimeRuntimeStateReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeRuntimeStateReconciler.class);

  private final RealtimeJobStore store;
  private final RealtimeEngineGateway gateway;
  private final SyncExecutionStateMachine stateMachine;
  private final TransactionTemplate transactions;

  public RealtimeRuntimeStateReconciler(
      RealtimeJobStore store,
      RealtimeEngineGateway gateway,
      SyncExecutionStateMachine stateMachine,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.store = store;
    this.gateway = gateway;
    this.stateMachine = stateMachine;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  void applyRuntimeState(
      long taskId,
      long deploymentId,
      String jobId,
      ComputeEnvironmentSnapshot runtimeEnvironment,
      RuntimeStatus runtime) {
    Boolean stop =
        transactions.execute(
            ignored -> {
              store.lockDefinition(taskId);
              DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
              if (latest == null
                  || latest.id() != deploymentId
                  || (hasText(latest.engineJobId()) && !Objects.equals(latest.engineJobId(), jobId))) {
                return false;
              }
              SyncExecution execution = latest.execution();

              if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
                transition(
                    taskId,
                    latest,
                    "UNKNOWN",
                    "UNKNOWN",
                    jobId,
                    "Flink 当前运行状态未知");
                return false;
              }
              if (execution.desiredState().name().equals("RUNNING")) {
                if (runtime.state() == RuntimeStatus.State.RUNNING) {
                  transition(taskId, latest, "RUNNING", "RUNNING", jobId, null);
                } else {
                  failExpectedRunning(
                      taskId,
                      latest,
                      runtime.state() == RuntimeStatus.State.TERMINATED
                          ? "Flink 任务已终止"
                          : "Flink 中未找到期望运行的任务");
                }
                return false;
              }
              if (runtime.state() == RuntimeStatus.State.RUNNING) {
                transition(taskId, latest, "STOPPING", "STOPPING", jobId, null);
                return true;
              }
              toStopped(taskId, latest, jobId, "Flink 已确认无活动任务");
              return false;
            });

    if (Boolean.TRUE.equals(stop)) {
      try {
        gateway.stop(runtimeEnvironment, jobId);
      } catch (RealtimeEngineException exception) {
        markUnknown(taskId, deploymentId, jobId, exception.getMessage());
        throw exception;
      }
    }
  }

  void settleMissing(long taskId, DeploymentRow deployment, String reason) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(taskId);
          DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
          if (!sameDeployment(deployment, latest) || hasText(latest.engineJobId())) {
            return;
          }
          SyncExecution execution = latest.execution();
          if (execution.desiredState().name().equals("RUNNING")) {
            failExpectedRunning(taskId, latest, "提交结果不确定，" + reason);
          } else {
            toStopped(taskId, latest, null, reason + "，已确认停止");
          }
        });
  }

  void markConflict(long taskId, DeploymentRow deployment, int count) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(taskId);
          DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
          if (!sameDeployment(deployment, latest) || hasText(latest.engineJobId())) {
            return;
          }
          SyncExecution execution = latest.execution();
          String target = execution.observedState() == ObservedState.STOPPING ? "UNKNOWN" : "CONFLICT";
          String message = "runtime job identity 匹配到 " + count + " 个 Flink Job，拒绝自动绑定";
          transition(taskId, latest, target, "UNKNOWN", null, message);
        });
  }

  void markEngineUnavailable(long taskId, String detail) {
    try {
      transactions.executeWithoutResult(
          ignored -> {
            store.lockDefinition(taskId);
            DeploymentRow deployment = store.latestDeployment(taskId).orElse(null);
            if (deployment == null) {
              return;
            }
            SyncExecution execution = deployment.execution();
            if (execution.terminal() || execution.observedState() == ObservedState.UNKNOWN) {
              return;
            }
            stateMachine.requireTransition(execution, "UNKNOWN");
            String message = "Flink 状态不可用" + (detail == null ? "" : "：" + detail);
            store.reconcile(
                taskId,
                deployment.id(),
                "UNKNOWN",
                "UNKNOWN",
                deployment.engineJobId(),
                message);
            store.event(
                taskId,
                deployment.id(),
                "FLINK_UNAVAILABLE",
                execution.observedState().name(),
                "UNKNOWN",
                message);
          });
    } catch (RuntimeException exception) {
      LOG.debug("Unable to mark realtime execution for task {} UNKNOWN", taskId, exception);
    }
  }

  private void markUnknown(long taskId, long deploymentId, String jobId, String message) {
    transactions.executeWithoutResult(
        ignored -> {
          store.lockDefinition(taskId);
          DeploymentRow latest = store.latestDeployment(taskId).orElse(null);
          if (latest == null || latest.id() != deploymentId) {
            return;
          }
          transition(taskId, latest, "UNKNOWN", "UNKNOWN", jobId, message);
        });
  }

  private void transition(
      long taskId,
      DeploymentRow deployment,
      String observed,
      String deploymentState,
      String jobId,
      String error) {
    SyncExecution execution = deployment.execution();
    stateMachine.requireTransition(execution, observed);
    store.reconcile(taskId, deployment.id(), observed, deploymentState, jobId, error);
    if (!observed.equals(execution.observedState().name())
        || !Objects.equals(error, execution.errorMessage())) {
      store.event(
          taskId,
          deployment.id(),
          "STATE_RECONCILED",
          execution.observedState().name(),
          observed,
          error == null ? "Flink 状态已对账" : error);
    }
  }

  private void failExpectedRunning(long taskId, DeploymentRow deployment, String message) {
    SyncExecution execution = deployment.execution();
    stateMachine.requireTransition(execution, "FAILED");
    store.markTerminalFailure(taskId, deployment.id(), message);
    store.event(
        taskId,
        deployment.id(),
        "FLINK_JOB_LOST",
        execution.observedState().name(),
        "FAILED",
        message);
  }

  private void toStopped(long taskId, DeploymentRow deployment, String jobId, String message) {
    ObservedState from = deployment.execution().observedState();
    if (from == ObservedState.STARTING || from == ObservedState.RUNNING) {
      stateMachine.requireTransition(from, ObservedState.STOPPING);
      store.reconcile(taskId, deployment.id(), "STOPPING", "STOPPING", jobId, null);
      from = ObservedState.STOPPING;
    }
    if (from != ObservedState.STOPPED) {
      stateMachine.requireTransition(from, ObservedState.STOPPED);
    }
    store.reconcile(taskId, deployment.id(), "STOPPED", "STOPPED", jobId, null);
    store.event(taskId, deployment.id(), "STOPPED", from.name(), "STOPPED", message);
  }

  private boolean sameDeployment(DeploymentRow expected, DeploymentRow current) {
    return expected != null && current != null && expected.id() == current.id();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
