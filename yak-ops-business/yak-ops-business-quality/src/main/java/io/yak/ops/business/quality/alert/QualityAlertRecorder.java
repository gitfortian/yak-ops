package io.yak.ops.business.quality.alert;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.AlertEventSpec;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.repository.QualityAlertRepository;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Records alert evidence when an execution result requires notification. */
@Component
@ConditionalOnQualityEnabled
public class QualityAlertRecorder {
  private static final Logger LOGGER = LoggerFactory.getLogger(QualityAlertRecorder.class);
  private final QualityAlertRepository repository;

  public QualityAlertRecorder(QualityAlertRepository repository) {
    this.repository = repository;
  }

  public void recordIfNecessary(
      QualityExecutionPlan plan,
      CheckResult result,
      int passed,
      int failed,
      int errors) {
    if (!plan.notifyEnabled()
        || result == CheckResult.PASSED
        || result == CheckResult.RUNNING
        || result == CheckResult.NOT_RUN) {
      return;
    }
    try {
      String target = normalizeTarget(plan);
      String status = plan.notifyChannel() == NotifyChannel.MESSAGE ? "RECORDED" : "PENDING";
      String message = "质量监控“" + plan.monitor().name() + "”执行结果为 " + result
          + "，通过 " + passed + " 条，未通过 " + failed + " 条，异常 " + errors + " 条。";
      repository.insertAlertEvent(new AlertEventSpec(
          plan.monitor().id(), plan.executionNo(), result, plan.alertLevel(), plan.notifyChannel(),
          target, status, message, null, LocalDateTime.now()));
      LOGGER.warn(
          "Quality alert recorded: monitor={}, execution={}, result={}, channel={}, target={}",
          plan.monitor().id(), plan.executionNo(), result, plan.notifyChannel(), target);
    } catch (RuntimeException exception) {
      LOGGER.error(
          "Failed to record quality alert for monitor {} and execution {}",
          plan.monitor().id(), plan.executionNo(), exception);
    }
  }

  private String normalizeTarget(QualityExecutionPlan plan) {
    if (plan.notifyTarget() != null && !plan.notifyTarget().isBlank()) return plan.notifyTarget().trim();
    return plan.monitor().owner();
  }
}
