package io.yak.ops.business.schedule.impl;

import io.yak.ops.business.schedule.ScheduleRegistrationService;
import io.yak.ops.business.task.submission.ScheduleAdmissionGate;
import io.yak.ops.common.bean.vo.schedule.ScheduleRegistrationVO;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.RegisteredSchedule;
import io.yak.ops.plugin.scheduler.api.ScheduleRegistration;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import jakarta.annotation.Resource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes registration changes, preserves unchanged triggers, and reports sanitized engine failures. */
@Service
public class ScheduleRegistrationServiceImpl implements ScheduleRegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleRegistrationServiceImpl.class);

    @Resource
    private ScheduleRepository schedules;

    @Resource
    private ScheduleFireRepository fires;

    @Resource
    private Scheduler scheduler;

    @Resource
    private CronService cron;

    @Resource
    private ScheduleAdmissionGate gate;

    @Resource
    private Clock scheduleClock;

    @Value("${yak.schedule.fire-retention:P30D}")
    private Duration retention;

    private final Map<String, String> errors = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public synchronized void reconcile() {
        gate.verifyOwnership();
        if (retention == null || retention.compareTo(gate.maxLateness()) <= 0) {
            throw new IllegalStateException("Schedule fire retention must exceed maximum lateness");
        }
        List<RegisteredSchedule> registered = scheduler.registrations();
        Set<String> present = new HashSet<>();
        for (RegisteredSchedule registration : registered) {
            present.add(key(
                    registration.registration().workspaceId(),
                    registration.registration().scheduleId()));
        }
        Instant before = gate.now().minus(retention);
        String cursor = null;
        while (true) {
            List<ScheduleEntity> rows = schedules.scan(cursor, 200);
            if (rows.isEmpty()) {
                break;
            }
            for (ScheduleEntity row : rows) {
                gate.verifyOwnership();
                if (row.isEnabled() || present.contains(key(row.getWorkspaceId(), row.getId()))) {
                    synchronize(row.getWorkspaceId(), row.getId());
                }
                if (row.getRetiredBefore() == null
                        || row.getRetiredBefore()
                                .isBefore(LocalDateTime.ofInstant(before.minus(Duration.ofHours(1)), ZoneOffset.UTC))) {
                    fires.retire(row.getWorkspaceId(), row.getId(), LocalDateTime.ofInstant(before, ZoneOffset.UTC));
                }
                present.remove(key(row.getWorkspaceId(), row.getId()));
                cursor = row.getId();
            }
        }
        for (RegisteredSchedule registration : registered) {
            ScheduleRegistration value = registration.registration();
            if (present.contains(key(value.workspaceId(), value.scheduleId()))) {
                synchronize(value.workspaceId(), value.scheduleId());
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void synchronize(String workspaceId, String scheduleId) {
        String identity = key(workspaceId, scheduleId);
        try {
            gate.verifyOwnership();
            var selected = schedules.queryIncludingDeleted(workspaceId, scheduleId);
            if (selected.isEmpty()
                    || selected.orElseThrow().getDeletedAt() != null
                    || !selected.orElseThrow().isEnabled()) {
                scheduler.remove(workspaceId, scheduleId);
                errors.remove(identity);
                return;
            }
            ScheduleEntity schedule = selected.orElseThrow();
            var current = scheduler.registration(workspaceId, scheduleId);
            Instant now = gate.now();
            if (current.isPresent()
                    && matches(schedule, current.orElseThrow().registration())
                    && ((current.orElseThrow().triggerPresent()
                                    && current.orElseThrow().firingEnabled())
                            || cron.nextFireTimes(schedule.getCronExpression(), schedule.getTimeZone(), now, 1)
                                    .isEmpty())) {
                errors.remove(identity);
                return;
            }
            Instant activation = now;
            if (schedule.getEnabledAt() != null
                    && schedule.getEnabledAt().toInstant(ZoneOffset.UTC).isAfter(activation)) {
                activation = schedule.getEnabledAt().toInstant(ZoneOffset.UTC);
            }
            scheduler.register(new ScheduleRegistration(
                    workspaceId,
                    scheduleId,
                    schedule.getRevision(),
                    schedule.getCronExpression(),
                    schedule.getTimeZone(),
                    activation));
            errors.remove(identity);
        } catch (RuntimeException failure) {
            errors.put(identity, "REGISTRATION_FAILED");
            LOG.error(
                    "Unable to synchronize schedule registration, scheduleId={}, exceptionType={}",
                    scheduleId,
                    failure.getClass().getSimpleName());
        }
    }

    @Override
    public Map<String, ScheduleRegistrationVO> describe(List<ScheduleEntity> rows) {
        Instant observed = scheduleClock.instant();
        Map<String, ScheduleRegistrationVO> result = new HashMap<>();
        Map<String, RegisteredSchedule> registrations = new HashMap<>();
        try {
            gate.verifyOwnership();
            for (RegisteredSchedule registered : scheduler.registrations()) {
                ScheduleRegistration value = registered.registration();
                registrations.put(key(value.workspaceId(), value.scheduleId()), registered);
            }
        } catch (RuntimeException failure) {
            for (ScheduleEntity row : rows) {
                result.put(
                        row.getId(),
                        new ScheduleRegistrationVO("ERROR", "SCHEDULER_UNAVAILABLE", observed, null, false));
            }
            return Map.copyOf(result);
        }
        for (ScheduleEntity row : rows) {
            String identity = key(row.getWorkspaceId(), row.getId());
            RegisteredSchedule current = registrations.get(identity);
            String status = "PENDING";
            String reason = errors.get(identity);
            Instant next = null;
            boolean exhausted = false;
            if (!row.isEnabled() || row.getDeletedAt() != null) {
                if (current == null) {
                    status = "APPLIED";
                    reason = null;
                }
            } else if (current != null && matches(row, current.registration())) {
                exhausted = cron.nextFireTimes(row.getCronExpression(), row.getTimeZone(), observed, 1)
                        .isEmpty();
                if ((current.triggerPresent() && current.firingEnabled()) || exhausted) {
                    status = "APPLIED";
                    reason = null;
                    next = current.nextFireTime();
                }
            }
            if (reason != null) {
                status = "ERROR";
            }
            result.put(row.getId(), new ScheduleRegistrationVO(status, reason, observed, next, exhausted));
        }
        return Map.copyOf(result);
    }

    private static boolean matches(ScheduleEntity row, ScheduleRegistration registration) {
        return row.getRevision() == registration.revision()
                && row.getCronExpression().equals(registration.cronExpression())
                && row.getTimeZone().equals(registration.timeZone());
    }

    private static String key(String workspace, String id) {
        return workspace + "/" + id;
    }
}
