package io.yak.ops.business.datamap.task.impl;

import io.yak.ops.business.datamap.crawl.DataMapCrawlService;
import io.yak.ops.business.datamap.crawl.DataMapCrawlTaskSpec;
import io.yak.ops.business.datamap.support.DataMapScope;
import io.yak.ops.business.datamap.task.DataMapCrawlCron;
import io.yak.ops.business.datamap.task.DataMapCrawlTaskService;
import io.yak.ops.business.modeling.planning.PlanningService;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlTaskSaveDTO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlTaskVO;
import io.yak.ops.common.bean.vo.datamap.DataMapCrawlVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.datamap.DataMapCrawlScheduleType;
import io.yak.ops.common.enums.datamap.DataMapCrawlTaskStatus;
import io.yak.ops.common.enums.datamap.DataMapCrawlTriggerType;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datamap.DataMapCrawlEntity;
import io.yak.ops.dao.entity.datamap.DataMapCrawlTaskEntity;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlMapper;
import io.yak.ops.dao.mapper.datamap.DataMapCrawlTaskMapper;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/**
 * Default task configuration and dispatch.
 *
 * <p>Lifecycle writes are conditional on the observed status, and the scheduler advances an
 * occurrence before submitting it, so a duplicated poll cannot start the same occurrence twice.
 */
@Service
public class DataMapCrawlTaskServiceImpl implements DataMapCrawlTaskService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapCrawlTaskServiceImpl.class);

    private static final int EXCLUDE_TABLE_LIMIT = 500;
    private static final int DUE_BATCH_LIMIT = 20;
    private static final int PREVIEW_COUNT = 3;

    @Resource
    private DataMapCrawlTaskMapper taskMapper;

    @Resource
    private DataMapCrawlMapper crawlMapper;

    @Resource
    private DataMapCrawlService crawlService;

    @Resource
    private DataSourceLookupService dataSourceLookup;

    @Resource
    private PlanningService planningService;

    @Override
    public PagingData<DataMapCrawlTaskVO> list(String workspaceId, int pageNo, int pageSize) {
        long total = taskMapper.count(workspaceId);
        List<DataMapCrawlTaskEntity> rows =
                total == 0 ? List.of() : taskMapper.listPage(workspaceId, pageSize, (pageNo - 1) * pageSize);
        Map<String, DataMapCrawlEntity> latest = latestRuns(workspaceId, rows);
        List<DataMapCrawlTaskVO> tasks =
                rows.stream().map(row -> toVO(row, latest.get(row.getId()))).toList();
        return PagingData.from(PageData.of(tasks, total, pageNo, pageSize));
    }

    @Override
    public DataMapCrawlTaskVO detail(String workspaceId, String taskId) {
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        return toVO(row, latestRuns(workspaceId, List.of(row)).get(row.getId()));
    }

    @Override
    public DataMapCrawlTaskVO create(String workspaceId, String actor, DataMapCrawlTaskSaveDTO input) {
        DataMapCrawlTaskStatus status = saveStatus(input.getStatus());
        TaskConfig config = validate(workspaceId, actor, status, input);
        DataMapCrawlTaskEntity row = new DataMapCrawlTaskEntity();
        apply(row, input, config, status);
        row.setWorkspaceId(workspaceId);
        row.initCreate(actor);
        taskMapper.insert(row);
        return toVO(row, null);
    }

    @Override
    public DataMapCrawlTaskVO update(String workspaceId, String actor, String taskId, DataMapCrawlTaskSaveDTO input) {
        DataMapCrawlTaskStatus status = saveStatus(input.getStatus());
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        TaskConfig config = validate(workspaceId, actor, status, input);
        apply(row, input, config, status);
        row.initUpdate(actor);
        if (taskMapper.update(row) == 0) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return toVO(row, latestRuns(workspaceId, List.of(row)).get(row.getId()));
    }

    @Override
    public DataMapCrawlTaskVO changeStatus(
            String workspaceId, String actor, String taskId, DataMapCrawlTaskStatus target) {
        if (target != DataMapCrawlTaskStatus.RUNNING && target != DataMapCrawlTaskStatus.OFFLINE) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_STATUS_INVALID);
        }
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        String current = row.getStatus();
        if (target.name().equals(current)) return detail(workspaceId, taskId);
        LocalDateTime nextFireTime = target.isOnline() ? nextFire(row) : null;
        int changed = taskMapper.transitionStatus(
                workspaceId, taskId, current, target.name(), nextFireTime, DateUtils.now(), auditActor(actor));
        if (changed == 0) throw new BusinessException(DataMapErrorCode.CRAWL_TASK_STATUS_INVALID);
        row.setStatus(target.name());
        row.setNextFireTime(nextFireTime);
        return toVO(row, latestRuns(workspaceId, List.of(row)).get(row.getId()));
    }

    @Override
    public void delete(String workspaceId, String taskId) {
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        if (DataMapCrawlTaskStatus.RUNNING.name().equals(row.getStatus())
                || crawlMapper.countActiveByTask(workspaceId, taskId) > 0) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_RUNNING);
        }
        if (taskMapper.delete(workspaceId, taskId) == 0) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_DELETE_FORBIDDEN);
        }
    }

    @Override
    public DataMapCrawlVO run(String workspaceId, String actor, String taskId) {
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        if (!DataMapCrawlTaskStatus.RUNNING.name().equals(row.getStatus())) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_NOT_RUNNABLE);
        }
        if (crawlMapper.countActiveByTask(workspaceId, taskId) > 0) {
            throw new BusinessException(DataMapErrorCode.CRAWL_BUSY);
        }
        return crawlService.submitTaskRun(workspaceId, actor, spec(row), DataMapCrawlTriggerType.MANUAL, null);
    }

    @Override
    public DataMapCrawlVO retry(String workspaceId, String actor, String taskId, String crawlId) {
        DataMapCrawlTaskEntity row = requireTask(workspaceId, taskId);
        return crawlService.submitTaskRun(workspaceId, actor, spec(row), DataMapCrawlTriggerType.MANUAL, crawlId);
    }

    @Override
    public List<LocalDateTime> previewSchedule(String cronExpression) {
        CronExpression cron;
        try {
            cron = DataMapCrawlCron.parse(cronExpression);
        } catch (RuntimeException invalid) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_CRON_INVALID);
        }
        List<LocalDateTime> occurrences = new ArrayList<>(PREVIEW_COUNT);
        LocalDateTime cursor = DateUtils.now();
        for (int index = 0; index < PREVIEW_COUNT; index++) {
            LocalDateTime next = DataMapCrawlCron.next(cron, cursor);
            if (next == null) break;
            occurrences.add(next);
            cursor = next;
        }
        return occurrences;
    }

    @Override
    public int dispatchDue() {
        LocalDateTime now = DateUtils.now();
        int submitted = 0;
        for (DataMapCrawlTaskEntity row : taskMapper.listDue(now, DUE_BATCH_LIMIT)) {
            LocalDateTime nextFireTime;
            try {
                CronExpression cron = DataMapCrawlCron.parse(row.getCronExpression());
                nextFireTime = DataMapCrawlCron.next(cron, now);
            } catch (RuntimeException invalid) {
                LOG.error("data-map crawl task {} skipped: Cron expression is invalid", row.getId(), invalid);
                continue;
            }
            if (taskMapper.advanceNextFire(row.getId(), row.getNextFireTime(), nextFireTime) == 0) continue;
            try {
                crawlService.submitTaskRun(
                        row.getWorkspaceId(),
                        CommonConstants.SYSTEM_USER,
                        spec(row),
                        DataMapCrawlTriggerType.SCHEDULED,
                        null);
                submitted++;
            } catch (BusinessException busy) {
                LOG.warn("data-map crawl task {} occurrence skipped: {}", row.getId(), busy.getErrorCode());
            } catch (RuntimeException failure) {
                LOG.error("data-map crawl task {} occurrence failed to start", row.getId(), failure);
            }
        }
        return submitted;
    }

    private DataMapCrawlTaskEntity requireTask(String workspaceId, String taskId) {
        DataMapCrawlTaskEntity row = taskMapper.findByIdForWorkspace(workspaceId, taskId);
        if (row == null) throw new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS);
        return row;
    }

    private TaskConfig validate(
            String workspaceId, String actor, DataMapCrawlTaskStatus status, DataMapCrawlTaskSaveDTO input) {
        DataSourceInfo datasource;
        try {
            datasource = dataSourceLookup.dataSource(workspaceId, StringUtils.trimToNull(input.getDatasourceId()));
        } catch (RuntimeException failure) {
            throw new BusinessException(DataMapErrorCode.CRAWL_SCOPE_INVALID);
        }
        DataMapScope scope = DataMapScope.resolve(input.getDatabase(), input.getSchema(), datasource);
        validateOwner(workspaceId, actor, input.getOwnerId());
        return new TaskConfig(scope, schedule(input, status), excludePattern(input), excludeTables(input));
    }

    private void validateOwner(String workspaceId, String actor, String ownerId) {
        String owner = StringUtils.trimToNull(ownerId);
        List<OwnerOptionVO> owners = planningService.owners(workspaceId, actor);
        if (owner == null || owners.stream().noneMatch(option -> owner.equals(option.userId()))) {
            throw new BusinessException(DataMapErrorCode.INVALID_OWNER);
        }
    }

    private static Schedule schedule(DataMapCrawlTaskSaveDTO input, DataMapCrawlTaskStatus status) {
        if (input.getScheduleType() == DataMapCrawlScheduleType.MANUAL) return new Schedule(null, null);
        String expression = StringUtils.trimToNull(input.getCronExpression());
        if (expression == null) throw new BusinessException(DataMapErrorCode.CRAWL_TASK_CRON_INVALID);
        CronExpression cron;
        try {
            cron = DataMapCrawlCron.parse(expression);
        } catch (RuntimeException invalid) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_CRON_INVALID);
        }
        return new Schedule(expression, status.isOnline() ? DataMapCrawlCron.next(cron, DateUtils.now()) : null);
    }

    private static String excludePattern(DataMapCrawlTaskSaveDTO input) {
        String pattern = StringUtils.trimToNull(input.getExcludePattern());
        if (pattern == null) return null;
        try {
            Pattern.compile(pattern);
        } catch (RuntimeException invalid) {
            throw new BusinessException(DataMapErrorCode.CRAWL_PATTERN_INVALID);
        }
        return pattern;
    }

    private static String excludeTables(DataMapCrawlTaskSaveDTO input) {
        List<String> tables = input.getExcludeTables();
        if (tables == null || tables.isEmpty()) return "[]";
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String table : tables) {
            String name = StringUtils.trimToNull(table);
            if (name != null) names.add(name);
        }
        if (names.size() > EXCLUDE_TABLE_LIMIT) {
            throw new BusinessException(DataMapErrorCode.CRAWL_EXCLUDE_TOO_MANY);
        }
        return JSONUtils.toJson(List.copyOf(names));
    }

    private static LocalDateTime nextFire(DataMapCrawlTaskEntity row) {
        if (!DataMapCrawlScheduleType.CRON.name().equals(row.getScheduleType())) return null;
        CronExpression cron;
        try {
            cron = DataMapCrawlCron.parse(row.getCronExpression());
        } catch (RuntimeException invalid) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_CRON_INVALID);
        }
        return DataMapCrawlCron.next(cron, DateUtils.now());
    }

    private static DataMapCrawlTaskStatus saveStatus(DataMapCrawlTaskStatus status) {
        if (status != DataMapCrawlTaskStatus.DRAFT && status != DataMapCrawlTaskStatus.RUNNING) {
            throw new BusinessException(DataMapErrorCode.CRAWL_TASK_STATUS_INVALID);
        }
        return status;
    }

    private static void apply(
            DataMapCrawlTaskEntity row,
            DataMapCrawlTaskSaveDTO input,
            TaskConfig config,
            DataMapCrawlTaskStatus status) {
        row.setName(input.getName().strip());
        row.setDatasourceId(input.getDatasourceId().strip());
        row.setDatabaseName(config.scope().databaseName());
        row.setSchemaName(config.scope().schemaName());
        row.setOwnerId(input.getOwnerId().strip());
        row.setStatus(status.name());
        row.setScheduleType(input.getScheduleType().name());
        row.setCronExpression(config.schedule().cronExpression());
        row.setNextFireTime(config.schedule().nextFireTime());
        row.setExcludePattern(config.excludePattern());
        row.setExcludeTables(config.excludeTables());
    }

    private static DataMapCrawlTaskSpec spec(DataMapCrawlTaskEntity row) {
        return new DataMapCrawlTaskSpec(
                row.getId(),
                row.getDatasourceId(),
                row.getDatabaseName(),
                row.getSchemaName(),
                row.getExcludePattern(),
                readTables(row.getExcludeTables()));
    }

    private static List<String> readTables(String excludeTables) {
        if (excludeTables == null || excludeTables.isBlank()) return List.of();
        return JSONUtils.parseList(excludeTables, String.class);
    }

    private Map<String, DataMapCrawlEntity> latestRuns(String workspaceId, List<DataMapCrawlTaskEntity> tasks) {
        if (tasks.isEmpty()) return Map.of();
        List<String> taskIds = tasks.stream().map(DataMapCrawlTaskEntity::getId).toList();
        return crawlMapper.listLatestByTaskIds(workspaceId, taskIds).stream()
                .collect(Collectors.toMap(DataMapCrawlEntity::getTaskId, Function.identity()));
    }

    private static DataMapCrawlTaskVO toVO(DataMapCrawlTaskEntity row, DataMapCrawlEntity latestRun) {
        DataMapCrawlTaskVO vo = new DataMapCrawlTaskVO();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setDatasourceId(row.getDatasourceId());
        vo.setDatabase(row.getDatabaseName());
        vo.setSchema(row.getSchemaName());
        vo.setOwnerId(row.getOwnerId());
        vo.setStatus(row.getStatus());
        vo.setScheduleType(row.getScheduleType());
        vo.setCronExpression(row.getCronExpression());
        vo.setNextFireTime(row.getNextFireTime());
        vo.setExcludePattern(row.getExcludePattern());
        vo.setExcludeTables(readTables(row.getExcludeTables()));
        vo.setCreatedTime(row.getCreateTime());
        vo.setUpdatedTime(row.getUpdateTime());
        vo.setCreateBy(row.getCreateBy());
        vo.setUpdateBy(row.getUpdateBy());
        if (latestRun != null) {
            vo.setLastRunStatus(latestRun.getStatus());
            vo.setLastRunTime(latestRun.getCreatedTime());
        }
        return vo;
    }

    private static String auditActor(String actor) {
        return StringUtils.trimToNull(actor) == null ? CommonConstants.SYSTEM_USER : actor;
    }

    private record Schedule(String cronExpression, LocalDateTime nextFireTime) {}

    private record TaskConfig(DataMapScope scope, Schedule schedule, String excludePattern, String excludeTables) {}
}
