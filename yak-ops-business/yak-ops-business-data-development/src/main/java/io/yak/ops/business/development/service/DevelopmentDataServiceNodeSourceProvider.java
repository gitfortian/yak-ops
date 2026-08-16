package io.yak.ops.business.development.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ParameterContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResolvedSource;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.ResponseFieldContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceContract;
import io.yak.ops.business.dataservice.service.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentDataServiceRevisionRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Exposes published Data Service Node revisions as the only new Data Development source for Runtime.
 *
 * <p>The stable source identity is the Data Service development node id. Each resolve points to the
 * latest published Data Service revision, which in turn pins one exact SQL TaskRevision. Runtime
 * never follows the SQL asset's latest pointer directly.
 */
@Component
@ConditionalOnDataSourceEnabled
public class DevelopmentDataServiceNodeSourceProvider implements DataServiceSourceProvider {

  public static final String SOURCE_TYPE = "DATA_DEVELOPMENT_DATA_SERVICE";

  private final DevelopmentNodeRepository nodeRepository;
  private final DevelopmentDataServiceRevisionRepository revisionRepository;
  private final TaskCatalogService taskCatalogService;
  private final ObjectMapper objectMapper;

  public DevelopmentDataServiceNodeSourceProvider(
      DevelopmentNodeRepository nodeRepository,
      DevelopmentDataServiceRevisionRepository revisionRepository,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper) {
    this.nodeRepository = nodeRepository;
    this.revisionRepository = revisionRepository;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
  }

  @Override
  public String sourceType() {
    return SOURCE_TYPE;
  }

  @Override
  public boolean managesServiceDefinition() {
    return true;
  }

  @Override
  public SourcePage list(int pageNo, int pageSize, String keyword) {
    int normalizedPageNo = Math.max(1, pageNo);
    int normalizedPageSize = Math.max(1, pageSize);
    String normalizedKeyword = StringUtils.hasText(keyword)
        ? keyword.trim().toLowerCase(Locale.ROOT)
        : null;

    List<DevelopmentNode> candidates = nodeRepository.list().stream()
        .filter(this::isDataServiceNode)
        .filter(node -> revisionRepository.findLatestByNodeId(node.id()).isPresent())
        .filter(node -> normalizedKeyword == null
            || node.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
        .sorted(Comparator
            .comparing(DevelopmentNode::updateTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(DevelopmentNode::id, Comparator.reverseOrder()))
        .toList();

    long total = candidates.size();
    long offset = (long) (normalizedPageNo - 1) * normalizedPageSize;
    int from = (int) Math.min(offset, candidates.size());
    int to = Math.min(from + normalizedPageSize, candidates.size());
    List<SourceDescriptor> records = new ArrayList<>(Math.max(0, to - from));
    for (DevelopmentNode node : candidates.subList(from, to)) {
      records.add(resolve(Long.toString(node.id())).descriptor());
    }
    return new SourcePage(records, total, normalizedPageNo, normalizedPageSize);
  }

  @Override
  public ResolvedSource resolve(String sourceRef) {
    long nodeId = parseNodeId(sourceRef);
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("Data Service Node 不存在：" + nodeId));
    if (!isDataServiceNode(node)) {
      throw new IllegalArgumentException("发布来源不是 Data Service Node：" + nodeId);
    }

    DevelopmentDataServiceRevision dataServiceRevision = revisionRepository
        .findLatestByNodeId(nodeId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Data Service Node 尚未发布 Revision：" + nodeId));
    DevelopmentDataServiceDefinition definition = dataServiceRevision.definition();
    if (definition == null) {
      throw new IllegalStateException("Data Service Revision 缺少定义：" + dataServiceRevision.id());
    }
    if (!"GET".equalsIgnoreCase(definition.method())) {
      throw new IllegalArgumentException("Data Service Runtime 当前仅支持 GET：" + definition.method());
    }
    validateContract(definition);

    ResolvedSqlRevision sql = resolvePinnedSql(node, definition);
    SourceDescriptor descriptor = new SourceDescriptor(
        SOURCE_TYPE,
        Long.toString(nodeId),
        definition.serviceName(),
        "DATA_SERVICE",
        "ONLINE",
        dataServiceRevision.id(),
        dataServiceRevision.revisionNo(),
        sql.dataSourceId(),
        definition.maxRows(),
        definition.timeoutSeconds(),
        definition.path(),
        definition.description(),
        dataServiceRevision.createTime());

    SourceContract contract = new SourceContract(
        definition.parameters().stream()
            .map(item -> new ParameterContract(
                item.name(), item.type(), item.required(), item.description(), item.example()))
            .toList(),
        definition.responseFields().stream()
            .map(item -> new ResponseFieldContract(
                item.name(), item.type(), item.nullable(), item.description(), item.example()))
            .toList());
    return new ResolvedSource(descriptor, sql.definition().content(), contract);
  }

  private ResolvedSqlRevision resolvePinnedSql(
      DevelopmentNode dataServiceNode,
      DevelopmentDataServiceDefinition definition) {
    TaskAsset asset = taskCatalogService.get(definition.sourceTaskAssetId());
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalStateException("Data Service Revision 的 SQL 来源不是数据开发资产");
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalStateException("Data Service Revision 的来源资产不是 SQL：" + asset.taskType());
    }
    if (!sameProject(asset.projectId(), dataServiceNode.projectId())) {
      throw new IllegalStateException("Data Service Revision 与 SQL 来源不属于同一项目");
    }

    TaskAssetRevision resolved = taskCatalogService.resolveRevision(
        asset.id(), definition.sourceTaskRevisionId());
    if (resolved.revision().revisionId() != definition.sourceTaskRevisionId()
        || resolved.revision().revisionNo() != definition.sourceTaskRevisionNo()) {
      throw new IllegalStateException(
          "Data Service Revision 固定的 SQL Revision 与实际解析结果不一致");
    }
    TaskDefinition sqlDefinition = resolved.revision().definition();
    if (!"SQL".equalsIgnoreCase(sqlDefinition.taskType())) {
      throw new IllegalStateException("Data Service Revision 固定的 TaskRevision 不是 SQL");
    }
    return new ResolvedSqlRevision(sqlDefinition, dataSourceId(sqlDefinition.configJson()));
  }

  private void validateContract(DevelopmentDataServiceDefinition definition) {
    for (DevelopmentDataServiceDefinition.ParameterContract parameter : definition.parameters()) {
      if (!parameter.required()) {
        throw new IllegalArgumentException(
            "Data Service Runtime 当前要求 SQL 命名参数为必填，请调整参数 Contract：" + parameter.name());
      }
      if ("OBJECT".equalsIgnoreCase(parameter.type())) {
        throw new IllegalArgumentException(
            "Data Service Runtime 请求参数暂不支持 OBJECT：" + parameter.name());
      }
    }
  }

  private Long dataSourceId(String configJson) {
    String raw = StringUtils.hasText(configJson) ? configJson.trim() : "{}";
    try {
      JsonNode root = objectMapper.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson 必须是 JSON Object");
      }
      String reference = root.path("dataSourceId").asText(null);
      if (!StringUtils.hasText(reference)) {
        throw new IllegalArgumentException("固定 SQL Revision 缺少 dataSourceId");
      }
      long value = Long.parseLong(reference.trim());
      if (value <= 0L) throw new IllegalArgumentException("固定 SQL Revision dataSourceId 必须大于 0");
      return value;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("固定 SQL Revision configJson 非法", exception);
    }
  }

  private boolean isDataServiceNode(DevelopmentNode node) {
    return node != null && DevelopmentNodeType.DATA_SERVICE.name().equalsIgnoreCase(node.type());
  }

  private long parseNodeId(String sourceRef) {
    if (!StringUtils.hasText(sourceRef)) throw new IllegalArgumentException("sourceRef 不能为空");
    try {
      long value = Long.parseLong(sourceRef.trim());
      if (value <= 0L) throw new NumberFormatException("not positive");
      return value;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Data Service Node sourceRef 必须是有效 nodeId：" + sourceRef,
          exception);
    }
  }

  private boolean sameProject(Long left, Long right) {
    Long normalizedLeft = left == null || left <= 0L ? null : left;
    Long normalizedRight = right == null || right <= 0L ? null : right;
    return Objects.equals(normalizedLeft, normalizedRight);
  }

  private record ResolvedSqlRevision(TaskDefinition definition, Long dataSourceId) {}
}
