package io.yak.ops.business.sync.offline.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.engine.LinkUpJobSpecFactory;
import io.yak.ops.business.sync.offline.engine.OfflineDefinitionModelAdapter;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobDefinitionDTO;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 离线同步任务定义规范化、JobSpec 构建与运行时凭据解析。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineDefinitionSupport {

  private final LinkUpJobSpecFactory jobSpecFactory;
  private final ObjectMapper objectMapper;

  public OfflineDefinitionSupport(
      LinkUpJobSpecFactory jobSpecFactory,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.jobSpecFactory = jobSpecFactory;
    this.objectMapper = objectMapper;
  }

  public PreparedDefinition prepare(OfflineJobDefinitionDTO dto) {
    ObjectNode request = normalizeRequest(dto);
    JsonNode basic = request.path("basic");
    String jobName = requiredText(basic, "jobName", "任务名称不能为空").trim();
    String mode = mode(basic);

    JsonNode buildRequest = OfflineDefinitionModelAdapter.forJobSpec(request, objectMapper);
    LinkUpJobSpecFactory.BuildResult result = jobSpecFactory.build(buildRequest);
    DataSourcePO source = result.getSourceDataSource();
    DataSourcePO sink = result.getSinkDataSource();

    return new PreparedDefinition(
        request,
        jobName,
        trim(text(basic, "jobDesc", null)),
        mode,
        write(request),
        result.getJobSpecJson(),
        id(source),
        id(sink),
        displayType(source, result.getSourceConnectorId()),
        displayType(sink, result.getSinkConnectorId()),
        result.getSourceTable(),
        result.getSinkTable(),
        digest(result.getJobSpecJson()));
  }

  public DraftDefinition prepareDraft(OfflineJobDefinitionDTO dto) {
    ObjectNode request = normalizeRequest(dto);
    JsonNode basic = request.path("basic");

    return new DraftDefinition(
        request,
        requiredText(basic, "jobName", "任务名称不能为空").trim(),
        trim(text(basic, "jobDesc", null)),
        mode(basic),
        write(request),
        endpointType(request.path("source"), "来源类型不能为空"),
        endpointType(request.path("sink"), "目标类型不能为空"));
  }

  public String buildJobSpec(OfflineJobDefinitionDTO dto) {
    return prepare(dto).getJobSpecJson();
  }

  public String resolveExecutionJobSpec(String logicalJobSpec) {
    return jobSpecFactory.resolveForExecution(logicalJobSpec);
  }

  public JsonNode editDetail(OfflineJobDefinition definition) {
    JsonNode parsed = read(definition.getDefinitionJson());
    ObjectNode detail =
        parsed != null && parsed.isObject()
            ? (ObjectNode) parsed.deepCopy()
            : objectMapper.createObjectNode();

    detail.put("id", definition.getId());
    ObjectNode state = detail.with("state");
    state.put("releaseState", definition.getReleaseState());
    state.put("lastJobStatus", definition.getLastJobStatus());
    state.put("lastErrorMessage", definition.getLastErrorMessage());
    state.set("lastExecutionId", objectMapper.valueToTree(definition.getLastExecutionId()));
    state.put("lastEngineJobId", definition.getLastEngineJobId());
    state.put("draft", !StringUtils.hasText(definition.getJobSpecJson()));
    return detail;
  }

  private ObjectNode normalizeRequest(OfflineJobDefinitionDTO dto) {
    if (dto == null) {
      throw new IllegalArgumentException("任务定义不能为空");
    }

    JsonNode value = objectMapper.valueToTree(dto);
    if (!value.isObject()) {
      throw new IllegalArgumentException("任务定义格式不正确");
    }

    ObjectNode request = (ObjectNode) value;
    requireObject(request.path("basic"), "basic 配置不能为空");
    normalizeEndpoint(request, "source");
    normalizeEndpoint(request, "sink");
    normalizeChannel(request);
    OfflineDefinitionModelAdapter.sanitizeForPersistence(request);
    return request;
  }

  private void normalizeEndpoint(ObjectNode request, String field) {
    requireObject(request.get(field), field + " 配置不能为空");
  }

  private void normalizeChannel(ObjectNode request) {
    JsonNode value = request.get("channel");
    ObjectNode channel;
    if (value == null || value.isNull()) {
      channel = request.putObject("channel");
    } else {
      requireObject(value, "channel 配置必须是 JSON 对象");
      channel = (ObjectNode) value;
    }

    putDefault(channel, "parallelism", 1);
    putDefault(channel, "speedLimitEnabled", false);
    putDefault(channel, "recordsPerSecond", 10000L);
    putDefault(channel, "dirtyDataPolicy", "STOP");
    putDefault(channel, "dirtyDataLimit", 0L);
  }

  private void putDefault(ObjectNode target, String field, int value) {
    if (!target.hasNonNull(field)) {
      target.put(field, value);
    }
  }

  private void putDefault(ObjectNode target, String field, long value) {
    if (!target.hasNonNull(field)) {
      target.put(field, value);
    }
  }

  private void putDefault(ObjectNode target, String field, boolean value) {
    if (!target.hasNonNull(field)) {
      target.put(field, value);
    }
  }

  private void putDefault(ObjectNode target, String field, String value) {
    if (!target.hasNonNull(field)) {
      target.put(field, value);
    }
  }

  private String mode(JsonNode basic) {
    String value = text(basic, "mode", "GUIDE_SINGLE");
    if (!"GUIDE_SINGLE".equals(value) && !"GUIDE_MULTI".equals(value)) {
      throw new IllegalArgumentException("离线同步仅支持 GUIDE_SINGLE 和 GUIDE_MULTI 模式");
    }
    return value;
  }

  private String endpointType(JsonNode endpoint, String message) {
    String value = text(endpoint, "dbType", null);
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private void requireObject(JsonNode node, String message) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException(message);
    }
  }

  private JsonNode read(String value) {
    if (!StringUtils.hasText(value)) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("任务定义 JSON 已损坏", exception);
    }
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化任务定义失败", exception);
    }
  }

  private String digest(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("生成 JobSpec 摘要失败", exception);
    }
  }

  private String requiredText(JsonNode node, String field, String message) {
    String value = text(node, field, null);
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() || !value.isValueNode()
        ? fallback
        : value.asText(fallback);
  }

  private String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private Long id(DataSourcePO dataSource) {
    return dataSource == null ? null : dataSource.getId();
  }

  private String displayType(DataSourcePO dataSource, String connectorId) {
    return dataSource != null && dataSource.getDbType() != null
        ? dataSource.getDbType().name()
        : connectorId;
  }

  public static final class DraftDefinition {
    private final ObjectNode request;
    private final String jobName;
    private final String jobDesc;
    private final String mode;
    private final String definitionJson;
    private final String sourceType;
    private final String sinkType;

    public DraftDefinition(
        ObjectNode request,
        String jobName,
        String jobDesc,
        String mode,
        String definitionJson,
        String sourceType,
        String sinkType) {
      this.request = request;
      this.jobName = jobName;
      this.jobDesc = jobDesc;
      this.mode = mode;
      this.definitionJson = definitionJson;
      this.sourceType = sourceType;
      this.sinkType = sinkType;
    }

    public ObjectNode getRequest() {
      return request;
    }

    public String getJobName() {
      return jobName;
    }

    public String getJobDesc() {
      return jobDesc;
    }

    public String getMode() {
      return mode;
    }

    public String getDefinitionJson() {
      return definitionJson;
    }

    public String getSourceType() {
      return sourceType;
    }

    public String getSinkType() {
      return sinkType;
    }
  }

  public static final class PreparedDefinition {
    private final ObjectNode request;
    private final String jobName;
    private final String jobDesc;
    private final String mode;
    private final String definitionJson;
    private final String jobSpecJson;
    private final Long sourceDatasourceId;
    private final Long sinkDatasourceId;
    private final String sourceType;
    private final String sinkType;
    private final String sourceTable;
    private final String sinkTable;
    private final String digest;

    public PreparedDefinition(
        ObjectNode request,
        String jobName,
        String jobDesc,
        String mode,
        String definitionJson,
        String jobSpecJson,
        Long sourceDatasourceId,
        Long sinkDatasourceId,
        String sourceType,
        String sinkType,
        String sourceTable,
        String sinkTable,
        String digest) {
      this.request = request;
      this.jobName = jobName;
      this.jobDesc = jobDesc;
      this.mode = mode;
      this.definitionJson = definitionJson;
      this.jobSpecJson = jobSpecJson;
      this.sourceDatasourceId = sourceDatasourceId;
      this.sinkDatasourceId = sinkDatasourceId;
      this.sourceType = sourceType;
      this.sinkType = sinkType;
      this.sourceTable = sourceTable;
      this.sinkTable = sinkTable;
      this.digest = digest;
    }

    public ObjectNode getRequest() {
      return request;
    }

    public String getJobName() {
      return jobName;
    }

    public String getJobDesc() {
      return jobDesc;
    }

    public String getMode() {
      return mode;
    }

    public String getDefinitionJson() {
      return definitionJson;
    }

    public String getJobSpecJson() {
      return jobSpecJson;
    }

    public Long getSourceDatasourceId() {
      return sourceDatasourceId;
    }

    public Long getSinkDatasourceId() {
      return sinkDatasourceId;
    }

    public String getSourceType() {
      return sourceType;
    }

    public String getSinkType() {
      return sinkType;
    }

    public String getSourceTable() {
      return sourceTable;
    }

    public String getSinkTable() {
      return sinkTable;
    }

    public String getDigest() {
      return digest;
    }
  }
}
