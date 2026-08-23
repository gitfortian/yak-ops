package io.yak.ops.business.sync.offline.definition;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.definition.OfflineDefinitionSupport.DraftDefinition;
import io.yak.ops.business.sync.offline.definition.OfflineDefinitionSupport.PreparedDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleLifecycle;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleSupport;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobDefinitionVO;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 离线同步当前定义的稳定 Application Facade，不维护独立版本表。 */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineJobDefinitionService {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineDefinitionSupport support;
  private final OfflineScheduleSupport scheduleSupport;
  private final OfflineScheduleLifecycle scheduleLifecycle;
  private final OfflineSyncViewMapper viewMapper;
  private final AtomicLong idSequence = new AtomicLong(System.currentTimeMillis() * 1000L);

  public Long nextId() {
    long floor = System.currentTimeMillis() * 1000L;
    long value = idSequence.updateAndGet(current -> Math.max(current + 1, floor));
    while (definitionRepository.findById(value).isPresent()) {
      value = idSequence.incrementAndGet();
    }
    return value;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public Long saveDraft(OfflineJobDefinitionDTO dto) {
    Long id = ensureDefinitionId(dto);
    OfflineJobDefinition existing = definitionRepository.findById(id).orElse(null);
    ensureEditable(existing);
    ensureCanSaveDraft(existing);

    DraftDefinition draft = support.prepareDraft(dto);
    ensureUniqueName(draft.getJobName(), id);

    OfflineJobDefinition definition = existing == null ? new OfflineJobDefinition() : existing;
    applyDraft(definition, existing, id, draft);
    persist(existing, definition);
    saveScheduleAndSync(id, draft.getRequest().get("schedule"));
    return id;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public Long saveGuide(OfflineJobDefinitionDTO dto) {
    Long id = ensureDefinitionId(dto);
    OfflineJobDefinition existing = definitionRepository.findById(id).orElse(null);
    ensureEditable(existing);

    PreparedDefinition prepared = support.prepare(dto);
    ensureUniqueName(prepared.getJobName(), id);

    OfflineJobDefinition definition = existing == null ? new OfflineJobDefinition() : existing;
    applyPrepared(definition, existing, id, prepared);
    persist(existing, definition);
    saveScheduleAndSync(id, prepared.getRequest().get("schedule"));
    return id;
  }

  public String buildGuideConfig(OfflineJobDefinitionDTO dto) {
    return support.buildJobSpec(dto);
  }

  public String resolveLogicalJobSpec(OfflineJobDefinition definition) {
    if (definition == null || !StringUtils.hasText(definition.getJobSpecJson())) {
      throw new IllegalStateException("任务仍是草稿，请完成配置并保存");
    }
    return definition.getJobSpecJson();
  }

  public String resolveExecutionJobSpec(OfflineJobDefinition definition) {
    return resolveExecutionJobSpec(resolveLogicalJobSpec(definition));
  }

  public String resolveExecutionJobSpec(String logicalJobSpecJson) {
    if (!StringUtils.hasText(logicalJobSpecJson)) {
      throw new IllegalStateException("任务版本快照缺少 JobSpec");
    }
    return support.resolveExecutionJobSpec(logicalJobSpecJson);
  }

  public OfflineJobDefinitionVO get(Long id) {
    validateId(id);
    OfflineJobDefinition definition = definitionRepository
        .findForViewById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + id));
    return viewMapper.definition(definition);
  }

  public JsonNode getEditDetail(Long id) {
    return support.editDetail(require(id));
  }

  public PageData<OfflineJobDefinition> pageDomain(OfflineDefinitionQuery query) {
    return definitionRepository.page(query);
  }

  public PagingData<OfflineJobDefinitionVO> page(OfflineJobDefinitionQueryDTO queryDTO) {
    OfflineJobDefinitionQueryDTO query =
        queryDTO == null ? new OfflineJobDefinitionQueryDTO() : queryDTO;
    OfflineDefinitionQuery domainQuery = new OfflineDefinitionQuery(
        query.getCurrent(),
        query.getPageSize(),
        query.getId(),
        query.getJobName(),
        query.getStatus(),
        query.getSourceType(),
        query.getSinkType(),
        query.getSourceTable(),
        query.getSinkTable(),
        query.getCreateTimeStart(),
        query.getCreateTimeEnd());
    return PagingData.from(definitionRepository.pageForView(domainQuery).map(viewMapper::definition));
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean online(Long id) {
    OfflineJobDefinition definition = require(id);
    resolveLogicalJobSpec(definition);

    definition.setReleaseState("ONLINE");
    definition.setUpdateTime(LocalDateTime.now());
    boolean updated = definitionRepository.update(definition);
    if (updated) {
      scheduleLifecycle.sync(id);
    }
    return updated;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean offline(Long id) {
    OfflineJobDefinition definition = require(id);
    ensureNoOccupyingBatch(id, "运行中的 BatchExecution 不能下线，请先停止任务");

    definition.setReleaseState("OFFLINE");
    definition.setUpdateTime(LocalDateTime.now());
    boolean updated = definitionRepository.update(definition);
    if (updated) {
      scheduleLifecycle.sync(id);
    }
    return updated;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public boolean delete(Long id) {
    OfflineJobDefinition definition = require(id);
    if ("ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("已上线任务不能删除，请先下线");
    }
    ensureNoOccupyingBatch(id, "运行中的 BatchExecution 不能删除");

    scheduleLifecycle.remove(id);
    return definitionRepository.delete(id);
  }

  public OfflineJobDefinition require(Long id) {
    validateId(id);
    return definitionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("离线同步任务不存在：" + id));
  }

  private Long ensureDefinitionId(OfflineJobDefinitionDTO dto) {
    if (dto == null) {
      throw new IllegalArgumentException("任务定义不能为空");
    }
    Long id = dto.getId();
    if (id == null || id <= 0L) {
      id = nextId();
      dto.setId(id);
    }
    return id;
  }

  private void ensureCanSaveDraft(OfflineJobDefinition existing) {
    if (existing != null && StringUtils.hasText(existing.getJobSpecJson())) {
      throw new IllegalStateException("已生成可执行配置的任务不能退回草稿");
    }
  }

  private void ensureUniqueName(String jobName, Long id) {
    if (definitionRepository.existsByName(jobName, id)) {
      throw new IllegalArgumentException("离线同步任务名称已存在：" + jobName);
    }
  }

  private void applyDraft(
      OfflineJobDefinition definition,
      OfflineJobDefinition existing,
      Long id,
      DraftDefinition draft) {
    LocalDateTime now = LocalDateTime.now();
    definition.setId(id);
    definition.setJobName(draft.getJobName());
    definition.setJobDesc(draft.getJobDesc());
    definition.setMode(draft.getMode());
    definition.setDefinitionJson(draft.getDefinitionJson());
    definition.setJobSpecJson(null);
    definition.setConfigDigest(null);
    definition.setReleaseState("OFFLINE");
    definition.setSourceType(draft.getSourceType());
    definition.setSinkType(draft.getSinkType());
    definition.setSourceDatasourceId(null);
    definition.setSinkDatasourceId(null);
    definition.setSourceTable(null);
    definition.setSinkTable(null);
    definition.setVersion(0);
    definition.setCreateTime(existing == null ? now : existing.getCreateTime());
    definition.setUpdateTime(now);
  }

  private void applyPrepared(
      OfflineJobDefinition definition,
      OfflineJobDefinition existing,
      Long id,
      PreparedDefinition prepared) {
    LocalDateTime now = LocalDateTime.now();
    definition.setId(id);
    definition.setJobName(prepared.getJobName());
    definition.setJobDesc(prepared.getJobDesc());
    definition.setMode(prepared.getMode());
    definition.setDefinitionJson(prepared.getDefinitionJson());
    definition.setJobSpecJson(prepared.getJobSpecJson());
    definition.setConfigDigest(prepared.getDigest());
    definition.setReleaseState(existing == null ? "OFFLINE" : existing.getReleaseState());
    definition.setSourceType(prepared.getSourceType());
    definition.setSinkType(prepared.getSinkType());
    definition.setSourceDatasourceId(prepared.getSourceDatasourceId());
    definition.setSinkDatasourceId(prepared.getSinkDatasourceId());
    definition.setSourceTable(prepared.getSourceTable());
    definition.setSinkTable(prepared.getSinkTable());
    definition.setVersion(nextVersion(existing));
    definition.setCreateTime(existing == null ? now : existing.getCreateTime());
    definition.setUpdateTime(now);
  }

  private int nextVersion(OfflineJobDefinition existing) {
    if (existing == null || existing.getVersion() == null) {
      return 1;
    }
    return Math.max(0, existing.getVersion()) + 1;
  }

  private void persist(OfflineJobDefinition existing, OfflineJobDefinition definition) {
    if (existing == null) {
      definitionRepository.insert(definition);
    } else {
      definitionRepository.update(definition);
    }
  }

  private void saveScheduleAndSync(Long id, JsonNode schedule) {
    scheduleRepository.saveSchedule(scheduleSupport.prepare(id, schedule));
    scheduleLifecycle.sync(id);
  }

  private void ensureEditable(OfflineJobDefinition definition) {
    if (definition == null) {
      return;
    }
    if ("ONLINE".equalsIgnoreCase(definition.getReleaseState())) {
      throw new IllegalStateException("已上线任务不能修改，请先下线");
    }
    ensureNoOccupyingBatch(definition.getId(), "运行中的 BatchExecution 不能修改");
  }

  private void ensureNoOccupyingBatch(Long id, String message) {
    if (batchRepository.hasOccupyingBatch(id)) {
      throw new IllegalStateException(message);
    }
  }

  private void validateId(Long id) {
    if (id == null || id <= 0L) {
      throw new IllegalArgumentException("任务定义 ID 不合法");
    }
  }
}
