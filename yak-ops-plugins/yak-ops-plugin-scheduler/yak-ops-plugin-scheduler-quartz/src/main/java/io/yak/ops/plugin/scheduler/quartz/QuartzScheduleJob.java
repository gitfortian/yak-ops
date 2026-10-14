package io.yak.ops.plugin.scheduler.quartz;

import io.yak.ops.plugin.scheduler.api.ScheduleRegistration;
import io.yak.ops.plugin.scheduler.api.ScheduleTrigger;
import io.yak.ops.plugin.scheduler.api.ScheduleTriggerListener;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Delivers immutable fire metadata; no Task, DAO, credential, or Job execution logic is held here. */
@DisallowConcurrentExecution
public final class QuartzScheduleJob implements Job {
    private static final Logger LOG = LoggerFactory.getLogger(QuartzScheduleJob.class);
    private final ScheduleTriggerListener listener;

    QuartzScheduleJob(ScheduleTriggerListener listener) {
        this.listener = listener;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            if (context.isRecovering() || context.getScheduledFireTime() == null || context.getRefireCount() != 0) {
                throw new IllegalStateException("Recovery or refiring cannot create schedule occurrences");
            }
            ScheduleRegistration registration =
                    QuartzScheduler.readRegistration(context.getTrigger().getJobDataMap());
            listener.onTrigger(new ScheduleTrigger(
                    registration.workspaceId(),
                    registration.scheduleId(),
                    registration.revision(),
                    context.getScheduledFireTime().toInstant(),
                    context.getFireTime().toInstant(),
                    registration.activatedAt()));
        } catch (RuntimeException failure) {
            LOG.error(
                    "Schedule notification could not be resolved, exceptionType={}",
                    failure.getClass().getSimpleName());
            // Do not put engine/DB diagnostic text into Quartz's user-readable Job result.
            throw new JobExecutionException(
                    "Schedule notification was not resolved; inspect application diagnostics", false);
        }
    }
}
