package io.yak.ops.business.development.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition.ParameterContract;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition.ResponseFieldContract;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDraft;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevisionSummary;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentDataServiceDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentDataServiceRevisionRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Data Service Node authoring lifecycle inside Data Development.
 *
 * <p>It deliberately stops at immutable DataServiceNodeRevision publication. Runtime deployment is
 * owned by the Data Service module and is connected in a later phase.
 */
@Service
public class DevelopmentDataServiceNodeService {

  private static final int DEFAULT_MAX_ROWS = 1_000;
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final int MAX_PREVIEW_TIMEOUT_SECONDS = 30;
  private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Set<String> SCHEMA_TYPES = Set.of(
      "STRING", "INTEGER", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "OBJECT");

  private final DevelopmentNodeRepository nodeRepository;
  private final TaskCatalogService taskCatalogService;
  private final DevelopmentDataServiceDraftRepository draftRepository;
  private final DevelopmentDataServiceRevisionRepository revisionRepository;
  private final DataSourceExecutionProvider dataSourceExecutionProvider;
  private final DevelopmentDataServiceSqlCompiler sqlCompiler;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceNodeService(
      DevelopmentNodeRepository nodeRepository,
      TaskCatalogService taskCatalogService,
      DevelopmentDataServiceDraftRepository draftRepository,
      DevelopmentDataServiceRevisionRepository revisionRepository,
      DataSourceExecutionProvider dataSourceExecutionProvider,
      DevelopmentDataServiceSqlCompiler sqlCompiler,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.taskCatalogService = taskCatalogService;
    this.draftRepository = draftRepository;
    this.revisionRepository = revisionRepository;
    this.dataSourceExecutionProvider = dataSourceExecutionProvider;
    this.sqlCompiler = sqlCompiler;
    this.objectMapper = objectMapper;
  }

  public DataServiceNodeContext get(long nodeId) {
    DevelopmentNode node = requireDataServiceNode(nodeId);
    DevelopmentDataServiceDraft draft = draftRepository.findByNodeId(nodeId)
        .orElseGet(() -> emptyDraft(node));
    List<DevelopmentDataServiceRevisionSummary> revisions = revisionRepository.listByNodeId(nodeId);
    return new DataServiceNodeContext(
        String.valueOf(node.id()),
        node.name(),
        node.configured() || draft.draftRevision() > 0L,
        availableSources(node),
        selectedSource(draft.definition()),
        draft,
        revisions.isEmpty() ? null : revisions.getFirst(),
        revisions);
  }

  public PreviewResult preview(long nodeId, long sourceTaskAssetId, long sourceTaskRevisionId) {
    DevelopmentNode node = requireDataServiceNode(nodeId);
    ResolvedSqlSource source = requireSqlSource(
        node, sourceTaskAssetId, sourceTaskRevisionId);
    ContractPreview contract = discoverContract(source.revision().revision().definition());
    return new PreviewResult(
        toSourceSnapshot(source.asset(), source.sourceNode(), source.revision()),
        contract.parameters(),
        contract.responseFields());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DataServiceNodeContext saveDraft(long nodeId, SaveDraftCommand command) {
    DevelopmentNode node = requireDataServiceNode(nodeId);
    if (command == null) throw new IllegalArgumentException("Data Service Node 草稿不能为空");

    ResolvedSqlSource source = requireSqlSource(
        node, command.sourceTaskAssetId(), command.sourceTaskRevisionId());
    TaskDefinition sqlDefinition = source.revision().revision().definition();
    List<String> sqlParameters = sqlCompiler.parameterNames(sqlDefinition.content());

    DevelopmentDataServiceDefinition definition = normalizeDefinition(
        node,
        source,
        command.serviceName(),
        command.path(),
        command.method(),
        command.parameters(),
        command.responseFields(),
        command.maxRows(),
        command.timeoutSeconds(),
        command.description(),
        sqlParameters,
        false);

    long expectedBaseRevision = command.baseRevision() == null ? 0L : command.baseRevision();
    DevelopmentDataServiceDraft saved = draftRepository
        .save(nodeId, definition, expectedBaseRevision)
        .orElseThrow(() -> new DevelopmentDraftConflictException(
            "Data Service Node 草稿已被其他会话更新，请刷新后重新保存（当前基线："
                + expectedBaseRevision + "）"));
    nodeRepository.updateConfigured(nodeId, true);
    return context(node, saved);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DevelopmentDataServiceRevision publish(long nodeId, long expectedDraftRevision) {
    DevelopmentNode node = requireDataServiceNode(nodeId);
    DevelopmentDataServiceDraft draft = draftRepository.findByNodeIdForUpdate(nodeId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Data Service Node 尚未保存草稿：" + nodeId));

    if (draft.draftRevision() != expectedDraftRevision) {
      throw new DevelopmentDraftConflictException(
          "发布失败：Data Service Node 草稿版本已变化，期望 "
              + expectedDraftRevision + "，当前 " + draft.draftRevision());
    }

    DevelopmentDataServiceDefinition current = draft.definition();
    ResolvedSqlSource source = requireSqlSource(
        node, current.sourceTaskAssetId(), current.sourceTaskRevisionId());
    List<String> sqlParameters = sqlCompiler.parameterNames(
        source.revision().revision().definition().content());
    DevelopmentDataServiceDefinition normalized = normalizeDefinition(
        node,
        source,
        current.serviceName(),
        current.path(),
        current.method(),
        current.parameters(),
        current.responseFields(),
        current.maxRows(),
        current.timeoutSeconds(),
        current.description(),
        sqlParameters,
        true);

    String checksum = checksum(normalized);
    DevelopmentDataServiceRevision latest =
        revisionRepository.findLatestByNodeId(nodeId).orElse(null);
    if (latest != null
        && latest.sourceDraftRevision() == draft.draftRevision()
        && Objects.equals(latest.checksum(), checksum)) {
      return latest;
    }

    DevelopmentDataServiceRevision published = revisionRepository.insert(
        nodeId,
        revisionRepository.nextRevisionNo(nodeId),
        draft.draftRevision(),
        normalized,
        checksum);
    nodeRepository.updateConfigured(nodeId, true);
    return published;
  }

  public List<DevelopmentDataServiceRevisionSummary> listRevisions(long nodeId) {
    requireDataServiceNode(nodeId);
    return revisionRepository.listByNodeId(nodeId);
  }

  public DevelopmentDataServiceRevision getRevision(long nodeId, int revisionNo) {
    requireDataServiceNode(nodeId);
    if (revisionNo <= 0) throw new IllegalArgumentException("revisionNo 必须大于 0");
    return revisionRepository.findByRevisionNo(nodeId, revisionNo)
        .orElseThrow(() -> new IllegalArgumentException(
            "Data Service Node 发布版本不存在：nodeId=" + nodeId + ", revisionNo=" + revisionNo));
  }

  private DataServiceNodeContext context(
      DevelopmentNode node,
      DevelopmentDataServiceDraft draft) {
    List<DevelopmentDataServiceRevisionSummary> revisions =
        revisionRepository.listByNodeId(node.id());
    DevelopmentNode refreshed = nodeRepository.findById(node.id()).orElse(node);
    return new DataServiceNodeContext(
        String.valueOf(refreshed.id()),
        refreshed.name(),
        refreshed.configured() || draft.draftRevision() > 0L,
        availableSources(refreshed),
        selectedSource(draft.definition()),
        draft,
        revisions.isEmpty() ? null : revisions.getFirst(),
        revisions);
  }

  private List<SourceSnapshot> availableSources(DevelopmentNode dataServiceNode) {
    return taskCatalogService.list("DATA_DEVELOPMENT", "ONLINE", null).stream()
        .filter(asset -> "SQL".equalsIgnoreCase(asset.taskType()))
        .filter(asset -> sameProject(asset.projectId(), dataServiceNode.projectId()))
        .map(asset -> toCurrentSourceIfValid(dataServiceNode, asset))
        .filter(Objects::nonNull)
        .sorted(Comparator
            .comparing(SourceSnapshot::nodeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SourceSnapshot::nodeId))
        .toList();
  }

  private SourceSnapshot toCurrentSourceIfValid(DevelopmentNode dataServiceNode, TaskAsset asset) {
    try {
      DevelopmentNode sourceNode = requireSourceSqlNode(asset);
      if (!sameProject(sourceNode.projectId(), dataServiceNode.projectId())) return null;
      TaskAssetRevision revision = taskCatalogService.resolveRevision(
          asset.id(), asset.currentRevision().taskRevisionId());
      return toSourceSnapshot(asset, sourceNode, revision);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private SourceSnapshot selectedSource(DevelopmentDataServiceDefinition definition) {
    if (definition == null
        || definition.sourceTaskAssetId() <= 0L
        || definition.sourceTaskRevisionId() <= 0L) {
      return null;
    }
    try {
      TaskAsset asset = taskCatalogService.get(definition.sourceTaskAssetId());
      DevelopmentNode sourceNode = nodeRepository.findById(parseSourceNodeId(asset)).orElse(null);
      TaskAssetRevision pinned = taskCatalogService.resolveRevision(
          asset.id(), definition.sourceTaskRevisionId());
      String nodeId = sourceNode == null ? asset.sourceRef() : String.valueOf(sourceNode.id());
      String nodeName = sourceNode == null ? asset.name() : sourceNode.name();
      return new SourceSnapshot(
          nodeId,
          nodeName,
          String.valueOf(asset.id()),
          asset.status().name(),
          String.valueOf(pinned.revision().revisionId()),
          pinned.revision().revisionNo(),
          String.valueOf(asset.currentRevision().taskRevisionId()),
          asset.currentRevision().revisionNo(),
          pinned.revision().revisionId() != asset.currentRevision().taskRevisionId());
    } catch (RuntimeException exception) {
      return new SourceSnapshot(
          null,
          "历史 SQL 来源",
          String.valueOf(definition.sourceTaskAssetId()),
          "UNAVAILABLE",
          String.valueOf(definition.sourceTaskRevisionId()),
          definition.sourceTaskRevisionNo(),
          null,
          null,
          false);
    }
  }

  private ResolvedSqlSource requireSqlSource(
      DevelopmentNode dataServiceNode,
      long sourceTaskAssetId,
      long sourceTaskRevisionId) {
    if (sourceTaskAssetId <= 0L) {
      throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    }
    if (sourceTaskRevisionId <= 0L) {
      throw new IllegalArgumentException("sourceTaskRevisionId 必须大于 0");
    }

    TaskAsset asset = taskCatalogService.get(sourceTaskAssetId);
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("Data Service Node 只能选择数据开发 SQL 来源");
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalArgumentException("Data Service Node 当前仅支持 SQL 来源");
    }
    if (asset.status() != TaskAssetStatus.ONLINE) {
      throw new IllegalArgumentException("Data Service Node 只能选择 ONLINE SQL 来源");
    }
    if (!sameProject(asset.projectId(), dataServiceNode.projectId())) {
      throw new IllegalArgumentException("Data Service Node 只能选择同项目的 SQL 来源");
    }

    DevelopmentNode sourceNode = requireSourceSqlNode(asset);
    if (!sameProject(sourceNode.projectId(), dataServiceNode.projectId())) {
      throw new IllegalArgumentException("Data Service Node 只能选择同项目的 SQL 节点");
    }

    TaskAssetRevision revision = taskCatalogService.resolveRevision(
        sourceTaskAssetId, sourceTaskRevisionId);
    if (revision.revision().revisionId() != sourceTaskRevisionId) {
      throw new IllegalStateException("Data Service Node 解析到的 SQL Revision 与请求不一致");
    }
    if (!"SQL".equalsIgnoreCase(revision.revision().definition().taskType())) {
      throw new IllegalArgumentException("Data Service Node 来源 Revision 不是 SQL");
    }
    sqlCompiler.validateSelectOnly(revision.revision().definition().content());
    return new ResolvedSqlSource(asset, sourceNode, revision);
  }

  private DevelopmentNode requireSourceSqlNode(TaskAsset asset) {
    long sourceNodeId = parseSourceNodeId(asset);
    DevelopmentNode sourceNode = nodeRepository.findById(sourceNodeId)
        .orElseThrow(() -> new IllegalStateException("SQL 来源节点不存在：" + sourceNodeId));
    if (!DevelopmentNodeType.SQL.name().equalsIgnoreCase(sourceNode.type())) {
      throw new IllegalStateException("TaskAsset 来源节点不是 SQL：" + sourceNodeId);
    }
    return sourceNode;
  }

  private long parseSourceNodeId(TaskAsset asset) {
    try {
      long nodeId = Long.parseLong(asset.sourceRef());
      if (nodeId <= 0L) throw new NumberFormatException("not positive");
      return nodeId;
    } catch (NumberFormatException exception) {
      throw new IllegalStateException(
          "SQL TaskAsset sourceRef 不是有效节点 ID：" + asset.sourceRef(), exception);
    }
  }

  private DevelopmentDataServiceDefinition normalizeDefinition(
      DevelopmentNode node,
      ResolvedSqlSource source,
      String serviceName,
      String path,
      String method,
      List<ParameterContract> requestedParameters,
      List<ResponseFieldContract> requestedResponseFields,
      Integer maxRows,
      Integer timeoutSeconds,
      String description,
      List<String> sqlParameterNames,
      boolean requireResponseContract) {
    String normalizedName = text(serviceName, node.name(), "服务名称", 200);
    String normalizedPath = normalizePath(path, node.id());
    String normalizedMethod = method == null || method.isBlank()
        ? "GET" : method.trim().toUpperCase(Locale.ROOT);
    if (!"GET".equals(normalizedMethod)) {
      throw new IllegalArgumentException("Data Service Node 第一阶段仅支持 GET");
    }

    List<ParameterContract> parameters =
        normalizeParameters(requestedParameters, sqlParameterNames);
    List<ResponseFieldContract> responseFields =
        normalizeResponseFields(requestedResponseFields);
    if (requireResponseContract && responseFields.isEmpty()) {
      throw new IllegalArgumentException("发布前请先预览并确认响应字段 Contract");
    }

    int normalizedMaxRows = range(
        maxRows, DEFAULT_MAX_ROWS, 1, 10_000, "最大返回行数");
    int normalizedTimeout = range(
        timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 1, 3_600, "超时时间");
    String normalizedDescription = optionalText(description, 2_000, "说明");

    return new DevelopmentDataServiceDefinition(
        source.asset().id(),
        source.revision().revision().revisionId(),
        source.revision().revision().revisionNo(),
        normalizedName,
        normalizedPath,
        normalizedMethod,
        parameters,
        responseFields,
        normalizedMaxRows,
        normalizedTimeout,
        normalizedDescription);
  }

  private List<ParameterContract> normalizeParameters(
      List<ParameterContract> values,
      List<String> sqlParameterNames) {
    Map<String, ParameterContract> requested = new LinkedHashMap<>();
    if (values != null) {
      for (ParameterContract value : values) {
        if (value == null) throw new IllegalArgumentException("请求参数 Contract 不能为空");
        String name = requiredParameterName(value.name());
        String key = name.toLowerCase(Locale.ROOT);
        if (requested.putIfAbsent(key, value) != null) {
          throw new IllegalArgumentException("请求参数重复：" + name);
        }
      }
    }

    List<ParameterContract> normalized = new ArrayList<>();
    Set<String> expected = new LinkedHashSet<>();
    for (String rawName : sqlParameterNames == null ? List.<String>of() : sqlParameterNames) {
      String name = requiredParameterName(rawName);
      String key = name.toLowerCase(Locale.ROOT);
      expected.add(key);
      ParameterContract value = requested.get(key);
      normalized.add(new ParameterContract(
          name,
          normalizeSchemaType(value == null ? "STRING" : value.type()),
          value == null || value.required(),
          optionalText(value == null ? null : value.description(), 1_000, "参数描述"),
          optionalText(value == null ? null : value.example(), 1_000, "参数示例")));
    }

    for (String key : requested.keySet()) {
      if (!expected.contains(key)) {
        throw new IllegalArgumentException(
            "请求参数不属于 SQL 命名参数：" + requested.get(key).name());
      }
    }
    return List.copyOf(normalized);
  }

  private List<ResponseFieldContract> normalizeResponseFields(
      List<ResponseFieldContract> values) {
    if (values == null || values.isEmpty()) return List.of();
    List<ResponseFieldContract> normalized = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (ResponseFieldContract value : values) {
      if (value == null) throw new IllegalArgumentException("响应字段 Contract 不能为空");
      String name = text(value.name(), null, "响应字段名称", 128);
      String key = name.toLowerCase(Locale.ROOT);
      if (!names.add(key)) throw new IllegalArgumentException("响应字段重复：" + name);
      normalized.add(new ResponseFieldContract(
          name,
          normalizeSchemaType(value.type()),
          value.nullable(),
          optionalText(value.description(), 1_000, "字段描述"),
          optionalText(value.example(), 1_000, "字段示例")));
    }
    return List.copyOf(normalized);
  }

  private ContractPreview discoverContract(TaskDefinition sqlDefinition) {
    List<String> parameterNames = sqlCompiler.parameterNames(sqlDefinition.content());
    List<ParameterContract> parameters = parameterNames.stream()
        .map(name -> new ParameterContract(name, "STRING", true, null, null))
        .toList();

    Map<String, Object> previewParameters = new LinkedHashMap<>();
    parameterNames.forEach(name -> previewParameters.put(name, null));
    DevelopmentDataServiceSqlCompiler.CompiledSql compiled =
        sqlCompiler.compile(sqlDefinition.content(), previewParameters);
    SourceConfig sourceConfig = sourceConfig(sqlDefinition.configJson());

    DataSourceSqlResult result;
    try (DataSourceSqlExecutor executor =
        dataSourceExecutionProvider.open(sourceConfig.dataSourceId())) {
      result = executor.execute(new DataSourceSqlRequest(
          compiled.sql(),
          1,
          Math.min(sourceConfig.timeoutSeconds(), MAX_PREVIEW_TIMEOUT_SECONDS),
          compiled.parameters()));
    }
    if (!result.resultSet()) {
      throw new IllegalStateException("Data Service Node Preview 没有返回结果集");
    }
    if (result.columns().isEmpty()) {
      throw new IllegalArgumentException("Data Service Node 来源查询没有可发现的响应字段");
    }

    List<ResponseFieldContract> responseFields = result.columns().stream()
        .map(this::toResponseField)
        .toList();
    return new ContractPreview(parameters, responseFields);
  }

  private ResponseFieldContract toResponseField(DataSourceSqlColumn column) {
    String name = column.label();
    if (name == null || name.isBlank()) name = column.name();
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Data Service Node 来源查询存在无名称响应字段");
    }
    return new ResponseFieldContract(
        name.trim(),
        schemaType(column.jdbcType()),
        column.nullable(),
        null,
        null);
  }

  private String schemaType(int jdbcType) {
    return switch (jdbcType) {
      case Types.BOOLEAN, Types.BIT -> "BOOLEAN";
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> "INTEGER";
      case Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> "NUMBER";
      case Types.DATE -> "DATE";
      case Types.TIME, Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
          "DATETIME";
      case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR,
          Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB -> "STRING";
      default -> "OBJECT";
    };
  }

  private SourceConfig sourceConfig(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson 必须是 JSON Object");
      }
      JsonNode dataSourceNode = root.get("dataSourceId");
      String dataSourceId = dataSourceNode == null || dataSourceNode.isNull()
          ? null : dataSourceNode.asText();
      if (dataSourceId == null || dataSourceId.isBlank()) {
        throw new IllegalArgumentException(
            "SQL TaskRevision 缺少 dataSourceId，无法预览 Data Service Contract");
      }
      int timeout = root.path("timeoutSeconds").asInt(DEFAULT_TIMEOUT_SECONDS);
      if (timeout < 1 || timeout > 3_600) timeout = DEFAULT_TIMEOUT_SECONDS;
      return new SourceConfig(dataSourceId.trim(), timeout);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL TaskRevision configJson 非法", exception);
    }
  }

  private SourceSnapshot toSourceSnapshot(
      TaskAsset asset,
      DevelopmentNode sourceNode,
      TaskAssetRevision pinnedRevision) {
    return new SourceSnapshot(
        String.valueOf(sourceNode.id()),
        sourceNode.name(),
        String.valueOf(asset.id()),
        asset.status().name(),
        String.valueOf(pinnedRevision.revision().revisionId()),
        pinnedRevision.revision().revisionNo(),
        String.valueOf(asset.currentRevision().taskRevisionId()),
        asset.currentRevision().revisionNo(),
        pinnedRevision.revision().revisionId() != asset.currentRevision().taskRevisionId());
  }

  private DevelopmentNode requireDataServiceNode(long nodeId) {
    if (nodeId <= 0L) throw new IllegalArgumentException("nodeId 必须大于 0");
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("数据开发节点不存在：" + nodeId));
    DevelopmentNodeType type = DevelopmentNodeType.tryParse(node.type()).orElse(null);
    if (type != DevelopmentNodeType.DATA_SERVICE) {
      throw new IllegalArgumentException("当前节点不是 Data Service Node：" + nodeId);
    }
    return node;
  }

  private DevelopmentDataServiceDraft emptyDraft(DevelopmentNode node) {
    return new DevelopmentDataServiceDraft(
        node.id(),
        new DevelopmentDataServiceDefinition(
            0L,
            0L,
            0,
            node.name(),
            "/query/" + node.id(),
            "GET",
            List.of(),
            List.of(),
            DEFAULT_MAX_ROWS,
            DEFAULT_TIMEOUT_SECONDS,
            null),
        0L,
        null,
        null);
  }

  private String normalizePath(String value, long nodeId) {
    String path = value == null || value.isBlank() ? "/query/" + nodeId : value.trim();
    if (!path.startsWith("/")) path = "/" + path;
    if (path.length() > 255) throw new IllegalArgumentException("服务路径不能超过 255 个字符");
    if (path.contains(" ") || path.contains("?") || path.contains("#") || path.contains("//")) {
      throw new IllegalArgumentException("服务路径格式非法：" + path);
    }
    return path;
  }

  private String requiredParameterName(String value) {
    String name = text(value, null, "请求参数名称", 128);
    if (!PARAMETER_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "请求参数名必须匹配 [A-Za-z_][A-Za-z0-9_]*：" + name);
    }
    return name;
  }

  private String normalizeSchemaType(String value) {
    String type = value == null || value.isBlank()
        ? "STRING" : value.trim().toUpperCase(Locale.ROOT);
    if (!SCHEMA_TYPES.contains(type)) {
      throw new IllegalArgumentException("不支持的 Contract 类型：" + value);
    }
    return type;
  }

  private String text(String value, String fallback, String label, int maxLength) {
    String normalized = value == null || value.isBlank() ? fallback : value.trim();
    if (normalized == null || normalized.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private String optionalText(String value, int maxLength, String label) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private int range(Integer value, int fallback, int min, int max, String label) {
    int normalized = value == null ? fallback : value;
    if (normalized < min || normalized > max) {
      throw new IllegalArgumentException(label + "必须在 " + min + " ~ " + max + " 之间");
    }
    return normalized;
  }

  private boolean sameProject(Long left, Long right) {
    return Objects.equals(normalizeProjectId(left), normalizeProjectId(right));
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  private String checksum(DevelopmentDataServiceDefinition definition) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(objectMapper.writeValueAsString(definition).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Data Service Node checksum 序列化失败", exception);
    }
  }

  public record SaveDraftCommand(
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      String serviceName,
      String path,
      String method,
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields,
      Integer maxRows,
      Integer timeoutSeconds,
      String description,
      Long baseRevision) {}

  public record SourceSnapshot(
      String nodeId,
      String nodeName,
      String taskAssetId,
      String status,
      String revisionId,
      Integer revisionNo,
      String currentRevisionId,
      Integer currentRevisionNo,
      boolean updateAvailable) {}

  public record PreviewResult(
      SourceSnapshot source,
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields) {}

  public record DataServiceNodeContext(
      String nodeId,
      String nodeName,
      boolean configured,
      List<SourceSnapshot> availableSources,
      SourceSnapshot selectedSource,
      DevelopmentDataServiceDraft draft,
      DevelopmentDataServiceRevisionSummary latestPublishedRevision,
      List<DevelopmentDataServiceRevisionSummary> revisions) {}

  private record ResolvedSqlSource(
      TaskAsset asset,
      DevelopmentNode sourceNode,
      TaskAssetRevision revision) {}

  private record ContractPreview(
      List<ParameterContract> parameters,
      List<ResponseFieldContract> responseFields) {}

  private record SourceConfig(String dataSourceId, int timeoutSeconds) {}
}
