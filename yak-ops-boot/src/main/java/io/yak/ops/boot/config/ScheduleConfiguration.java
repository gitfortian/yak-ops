package io.yak.ops.boot.config;

import io.yak.ops.business.task.submission.ScheduleAdmissionGate;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import io.yak.ops.plugin.scheduler.quartz.QuartzCronService;
import io.yak.ops.plugin.scheduler.quartz.QuartzScheduler;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles scheduler collaborators without initializing its store before verified ownership. */
@Configuration(proxyBeanMethods = false)
public class ScheduleConfiguration {
    @Bean
    Clock scheduleClock() {
        return Clock.systemUTC();
    }

    @Bean
    CronService scheduleCronService() {
        return new QuartzCronService();
    }

    @Bean
    ScheduleAdmissionGate scheduleAdmissionGate(
            Clock scheduleClock, @Value("${yak.schedule.max-lateness:PT60S}") Duration maxLateness) {
        return new ScheduleAdmissionGate(scheduleClock, maxLateness);
    }

    @Bean(destroyMethod = "")
    Scheduler scheduleEngine(DataSource dataSource, SubmissionService submissions, ScheduleAdmissionGate gate) {
        return new QuartzScheduler(dataSource, trigger -> submissions.submitScheduled(trigger), gate.maxLateness());
    }
}
