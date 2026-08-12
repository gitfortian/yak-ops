package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskValidationIssue;
import io.yak.ops.plugin.task.api.TaskValidationResult;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoring lifecycle: mutable draft -> validated immutable published revision -> Task Catalog. */
@Service
public class DevelopmentTaskService {

  private final DevelopmentNodeRepository nodeRepository;
  private final DevelopmentTaskDraftRepository draftRepository;
  private final DevelopmentTaskRevisionRepository revisionRepository;
  private final TaskCatalogService taskCatalogService;
  private final TaskPluginRegistry pluginRegistry;
  private final ObjectMapper objectMapper;

  public DevelopmentTaskService(
      DevelopmentNodeRepository nodeRepository,
      DevelopmentTaskDraftRepository draftRepository,
      DevelopmentTaskRevisionRepository revisionRepository,
      TaskCatalogService taskCatalogService,
      TaskPluginRegistry pluginRegistry,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.draftRepository = draftRepository;
    this.revisionRepository = revisionRepository;
    this.taskCatalogService = taskCatalogService;
    this.pluginRegistry = pluginRegistry;
    this.objectMapper = objectMapper;
  }

  public DevelopmentTaskDraft getDraft(Long nodeId) {
    DevelopmentNode node = requireNode(nodeId);
    return draftRepository.findByNodeId(nodeId)
        .orElseGet(() -> emptyDraft(node));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentTaskDraft saveDraft(
      Long nodeId,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      Long baseRevision) {
    DevelopmentNode node = requireNode(nodeId);
    TaskDefinition definition = normalizeDefinition(
        node,
        taskType,
        schemaVersion,
        content,
        configJson);
    long expectedRevision = baseRevision == null ? 0L : baseRevision;

    DevelopmentTaskDraft saved = draftRepository.save(nodeId, definition, expectedRevision)
        .orElseThrow(() -> new DevelopmentDraftConflictException(
            "草稿已被其他会话更新，请刷新后重新保存（当前基线：" + expectedRevision + "）"));
    nodeRepository.updateConfigured(nodeId, true);
    return saved;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentTaskRevision publish(Long nodeId, long expectedDraftRevision) {
    DevelopmentNode node = requireNode(nodeId);
    DevelopmentTaskDraft draft = draftRepository.findByNodeIdForUpdate(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点尚未保存草稿：" + nodeId));

    if (draft.draftRevision() != expectedDraftRevision) {
      throw new DevelopmentDraftConflictException(
          "发布失败：草稿版本已变化，期望 "
              + expectedDraftRevision
              + "，当前 "
              + draft.draftRevision());
    }

    TaskDefinition definition = normalizeDefinition(
        node,
        draft.definition().taskType(),
        draft.definition().schemaVersion(),
        draft.definition().content(),
        draft.definition().configJson());
    validateForPublish(definition);

    String checksum = checksum(definition);
    DevelopmentTaskRevision latest = revisionRepository.findLatestByNodeId(nodeId).orElse(null);
    DevelopmentTaskRevision published;
    if (latest != null
        && latest.sourceDraftRevision() == draft.draftRevision()
        && Objects.equals(latest.checksum(), checksum)) {
      published = latest;
    } else {
      int revisionNo = revisionRepository.nextRevisionNo(nodeId);
      published = revisionRepository.insert(
          nodeId,
          revisionNo,
          draft.draftRevision(),
          definition,
          checksum);
    }

    nodeRepository.updateConfigured(nodeId, true);
    taskCatalogService.publish(
        TaskAssetSource.DATA_DEVELOPMENT,
        String.valueOf(node.id()),
        node.projectId(),
        node.name(),
        definition.taskType(),
        published.id(),
        published.revisionNo());
    return published;
  }

  public List<DevelopmentTaskRevisionSummary> listRevisions(Long nodeId) {
    requireNode(nodeId);
    return revisionRepository.listByNodeId(nodeId);
  }

  public DevelopmentTaskRevision getRevision(Long nodeId, int revisionNo) {
    requireNode(nodeId);
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo 必须大于 0");
    return revisionRepository.findByRevisionNo(nodeId, revisionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "发布版本不存在：nodeId=" + nodeId + ", revisionNo=" + revisionNo));
  }

  private DevelopmentNode requireNode(Long nodeId) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    return nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
  }

  private DevelopmentTaskDraft emptyDraft(DevelopmentNode node) {
    int schemaVersion = pluginRegistry.find(node.type())
        .map(plugin -> plugin.descriptor().schemaVersion())
        .orElse(1);
    return new DevelopmentTaskDraft(
        node.id(),
        new TaskDefinition(node.type(), schemaVersion, "", "{}"),
        0L,
        null,
        null);
  }

  private TaskDefinition normalizeDefinition(
      DevelopmentNode node,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("taskType 不能为空");
    }
    String normalizedType = taskType.trim().toUpperCase(Locale.ROOT);
    if (!normalizedType.equals(node.type().trim().toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "任务类型与节点类型不一致：node=" + node.type() + ", definition=" + normalizedType);
    }
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion 必须大于 0");
    }

    return new TaskDefinition(
        normalizedType,
        schemaVersion,
        content == null ? "" : content,
        normalizeConfigJson(configJson));
  }

  private String normalizeConfigJson(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("configJson 必须是 JSON Object");
      }
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("configJson 不是合法 JSON", exception);
    }
  }

  private void validateForPublish(TaskDefinition definition) {
    TaskPlugin plugin = pluginRegistry.find(definition.taskType())
        .orElseThrow(() -> new DevelopmentTaskValidationException(
            "当前未安装 " + definition.taskType() + " Task Plugin，无法发布",
            List.of(new TaskValidationIssue(
                "TASK_PLUGIN_NOT_INSTALLED",
                "taskType",
                "Task plugin is not installed: " + definition.taskType()))));

    TaskValidationResult validation = plugin.validate(definition);
    if (!validation.valid()) {
      String summary = validation.issues().stream()
          .map(TaskValidationIssue::message)
          .limit(3)
          .reduce((left, right) -> left + "；" + right)
          .orElse("任务定义校验失败");
      throw new DevelopmentTaskValidationException(summary, validation.issues());
    }
  }

  private String checksum(TaskDefinition definition) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateDigest(digest, definition.taskType());
      updateDigest(digest, Integer.toString(definition.schemaVersion()));
      updateDigest(digest, definition.content());
      updateDigest(digest, definition.configJson());
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private void updateDigest(MessageDigest digest, String value) {
    if (value != null) digest.update(value.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
  }
}
