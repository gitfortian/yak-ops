package io.yak.ops.plugin.scheduler.quartz;

import io.yak.ops.plugin.scheduler.api.RegisteredSchedule;
import io.yak.ops.plugin.scheduler.api.ScheduleRegistration;
import io.yak.ops.plugin.scheduler.api.ScheduleTriggerListener;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.utils.ConnectionProvider;
import org.quartz.utils.DBConnectionManager;

/** Single-owner persistent Quartz adapter; construction performs no store or thread initialization. */
public final class QuartzScheduler implements Scheduler {
    private static final String NAME = "YakOpsSchedule";
    private static final String GROUP = "yak-ops-schedules";
    private final DataSource dataSource;
    private final ScheduleTriggerListener listener;
    private final Duration maxLateness;
    private final QuartzCronService cron = new QuartzCronService();
    private org.quartz.Scheduler engine;

    /**
     * Retains application-owned dependencies until the verified lifecycle initializes the store.
     * @param dataSource platform pool whose lifecycle remains application-owned
     * @param listener short business submission callback
     * @param maxLateness business lateness limit used as the Quartz misfire threshold
     */
    public QuartzScheduler(DataSource dataSource, ScheduleTriggerListener listener, Duration maxLateness) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.listener = Objects.requireNonNull(listener);
        this.maxLateness = Objects.requireNonNull(maxLateness);
        if (maxLateness.toMillis() < 1) {
            throw new IllegalArgumentException("Quartz misfire threshold must be positive");
        }
    }

    @Override
    public synchronized void initialize() {
        if (engine != null) {
            throw new IllegalStateException("Quartz scheduler has already been initialized");
        }
        AtomicReference<DataSource> connectionSource = new AtomicReference<>(dataSource);
        DBConnectionManager.getInstance().addConnectionProvider(NAME, new ConnectionProvider() {
            @Override
            public Connection getConnection() throws SQLException {
                DataSource current = connectionSource.get();
                if (current == null) {
                    throw new SQLException("Schedule connection provider is closed");
                }
                return current.getConnection();
            }

            @Override
            public void initialize() {}

            @Override
            public void shutdown() {
                connectionSource.set(null);
            }
        });
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", NAME);
        properties.setProperty("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
        properties.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        properties.setProperty("org.quartz.scheduler.idleWaitTime", "1000");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "4");
        properties.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty(
                "org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.dataSource", NAME);
        properties.setProperty("org.quartz.jobStore.tablePrefix", "YAK_QRTZ_");
        properties.setProperty("org.quartz.jobStore.useProperties", "true");
        properties.setProperty("org.quartz.jobStore.isClustered", "false");
        properties.setProperty("org.quartz.jobStore.misfireThreshold", Long.toString(maxLateness.toMillis()));
        try {
            engine = new StdSchedulerFactory(properties).getScheduler();
            engine.setJobFactory((bundle, scheduler) -> {
                if (bundle.getJobDetail().getJobClass() != QuartzScheduleJob.class) {
                    throw new SchedulerException("Unsupported schedule job class");
                }
                return new QuartzScheduleJob(listener);
            });
        } catch (SchedulerException failure) {
            connectionSource.set(null);
            close();
            throw new IllegalStateException("Unable to initialize Quartz schedule store", failure);
        }
    }

    @Override
    public synchronized void start() {
        try {
            requireEngine().start();
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to start Quartz scheduler", failure);
        }
    }

    @Override
    public synchronized void standby() {
        if (engine == null) {
            return;
        }
        try {
            engine.standby();
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to pause Quartz scheduler", failure);
        }
    }

    @Override
    public synchronized void register(ScheduleRegistration registration) {
        Objects.requireNonNull(registration);
        cron.validate(registration.cronExpression(), registration.timeZone());
        if (registration.revision() < 1 || registration.activatedAt() == null) {
            throw new IllegalArgumentException("A registration requires revision and activation time");
        }
        Optional<RegisteredSchedule> current = registration(registration.workspaceId(), registration.scheduleId());
        List<Instant> next = cron.nextFireTimes(
                registration.cronExpression(), registration.timeZone(), registration.activatedAt(), 1);
        if (current.isPresent()
                && current.orElseThrow().registration().equals(registration)
                && ((current.orElseThrow().triggerPresent()
                                && current.orElseThrow().firingEnabled())
                        || next.isEmpty())) {
            return;
        }
        JobKey key = key(registration.workspaceId(), registration.scheduleId());
        JobDataMap data = data(registration);
        JobDetail job = JobBuilder.newJob(QuartzScheduleJob.class)
                .withIdentity(key)
                .storeDurably()
                .requestRecovery(false)
                .usingJobData(data)
                .build();
        try {
            requireEngine().addJob(job, true);
            TriggerKey triggerKey = new TriggerKey(key.getName(), GROUP);
            if (next.isEmpty()) {
                requireEngine().unscheduleJob(triggerKey);
                return;
            }
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(key)
                    .startAt(Date.from(next.getFirst()))
                    .usingJobData(data(registration))
                    .withSchedule(CronScheduleBuilder.cronSchedule(registration.cronExpression())
                            .inTimeZone(TimeZone.getTimeZone(ZoneId.of(registration.timeZone())))
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            if (requireEngine().checkExists(triggerKey)) {
                requireEngine().rescheduleJob(triggerKey, trigger);
                requireEngine().resumeTrigger(triggerKey);
            } else {
                requireEngine().scheduleJob(trigger);
            }
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to apply Quartz registration", failure);
        }
    }

    @Override
    public synchronized void remove(String workspaceId, String scheduleId) {
        try {
            requireEngine().deleteJob(key(workspaceId, scheduleId));
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to remove Quartz registration", failure);
        }
    }

    @Override
    public synchronized Optional<RegisteredSchedule> registration(String workspaceId, String scheduleId) {
        try {
            return observe(key(workspaceId, scheduleId));
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to inspect Quartz registration", failure);
        }
    }

    @Override
    public synchronized List<RegisteredSchedule> registrations() {
        try {
            List<RegisteredSchedule> registrations = new ArrayList<>();
            for (JobKey key : requireEngine().getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
                observe(key).ifPresent(registrations::add);
            }
            return List.copyOf(registrations);
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to inspect Quartz registrations", failure);
        }
    }

    private Optional<RegisteredSchedule> observe(JobKey key) throws SchedulerException {
        JobDetail job = requireEngine().getJobDetail(key);
        if (job == null) {
            return Optional.empty();
        }
        Trigger trigger = requireEngine().getTrigger(new TriggerKey(key.getName(), GROUP));
        // Trigger data is authoritative when replacement stopped between the JobDetail and Trigger writes.
        JobDataMap data = job.getJobDataMap();
        if (trigger != null) {
            data = trigger.getJobDataMap();
        }
        Instant next = null;
        if (trigger != null && trigger.getNextFireTime() != null) {
            next = trigger.getNextFireTime().toInstant();
        }
        Trigger.TriggerState state = requireEngine().getTriggerState(new TriggerKey(key.getName(), GROUP));
        boolean firingEnabled = state == Trigger.TriggerState.NORMAL || state == Trigger.TriggerState.BLOCKED;
        return Optional.of(new RegisteredSchedule(readRegistration(data), trigger != null, firingEnabled, next));
    }

    @Override
    public synchronized void close() {
        if (engine == null) {
            return;
        }
        try {
            engine.shutdown(true);
        } catch (SchedulerException failure) {
            throw new IllegalStateException("Unable to close Quartz scheduler", failure);
        } finally {
            engine = null;
        }
    }

    private org.quartz.Scheduler requireEngine() {
        if (engine == null) {
            throw new IllegalStateException("Quartz store requires verified initialization");
        }
        return engine;
    }

    private static JobKey key(String workspace, String schedule) {
        if (workspace == null
                || workspace.isBlank()
                || workspace.contains("/")
                || schedule == null
                || schedule.isBlank()
                || schedule.contains("/")) {
            throw new IllegalArgumentException("Invalid schedule registration identity");
        }
        return new JobKey(workspace + "/" + schedule, GROUP);
    }

    private static JobDataMap data(ScheduleRegistration registration) {
        JobDataMap data = new JobDataMap();
        data.put("workspace", registration.workspaceId());
        data.put("schedule", registration.scheduleId());
        data.put("revision", Long.toString(registration.revision()));
        data.put("cron", registration.cronExpression());
        data.put("zone", registration.timeZone());
        data.put("activation", registration.activatedAt().toString());
        return data;
    }

    static ScheduleRegistration readRegistration(JobDataMap data) {
        return new ScheduleRegistration(
                data.getString("workspace"),
                data.getString("schedule"),
                Long.parseLong(data.getString("revision")),
                data.getString("cron"),
                data.getString("zone"),
                Instant.parse(data.getString("activation")));
    }
}
