package io.yak.ops.business.development.service;

import io.yak.ops.business.dataset.DevelopmentDatasetFacade;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.FieldDraft;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade.NodeDataset;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a standalone DATASET node into a stable Dataset asset.
 *
 * <p>Source selection belongs to the Dataset node itself. Workflow topology is intentionally not
 * consulted here: orchestration relationships are owned by the dedicated workflow module. Saving the
 * Dataset snapshots the selected SQL TaskAsset's current immutable TaskRevision.
 */
@Service
public class DevelopmentDatasetNodeService {

  private final DevelopmentNodeRepository nodeRepository;
  private final TaskCatalogService taskCatalogService;
  private final DevelopmentDatasetFacade datasetFacade;

  public DevelopmentDatasetNodeService(
      DevelopmentNodeRepository nodeRepository,
      TaskCatalogService taskCatalogService,
      DevelopmentDatasetFacade datasetFacade) {
    this.nodeRepository = nodeRepository;
    this.taskCatalogService = taskCatalogService;
    this.datasetFacade = datasetFacade;
  }

  public DatasetNodeContext get(long nodeId) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    NodeDataset dataset = datasetFacade.findByDevelopmentNodeId(nodeId).orElse(null);
    return context(datasetNode, dataset, selectedSource(dataset));
  }

  public List<FieldDraft> preview(long nodeId, long sourceTaskAssetId) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    TaskAsset source = requireSelectableSqlAsset(datasetNode, sourceTaskAssetId);
    return datasetFacade.preview(source.id());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DatasetNodeContext save(
      long nodeId,
      long sourceTaskAssetId,
      String description,
      List<FieldDraft> fields) {
    DevelopmentNode datasetNode = requireDatasetNode(nodeId);
    TaskAsset source = requireSelectableSqlAsset(datasetNode, sourceTaskAssetId);
    NodeDataset saved = datasetFacade.save(
        nodeId,
        source.id(),
        datasetNode.name(),
        description,
        fields);
    if (!datasetNode.configured() && !nodeRepository.updateConfigured(nodeId, true)) {
      throw new IllegalStateException("Dataset Node 配置状态更新失败：" + nodeId);
    }
    DevelopmentNode refreshed = nodeRepository.findById(nodeId).orElse(datasetNode);
    return context(refreshed, saved, toSnapshot(source));
  }

  private DatasetNodeContext context(
      DevelopmentNode datasetNode,
      NodeDataset dataset,
      SourceSnapshot selectedSource) {
    return new DatasetNodeContext(
        String.valueOf(datasetNode.id()),
        datasetNode.name(),
        datasetNode.configured() || dataset != null,
        availableSources(datasetNode),
        selectedSource,
        dataset);
  }

  private List<SourceSnapshot> availableSources(DevelopmentNode datasetNode) {
    return taskCatalogService.list("DATA_DEVELOPMENT", "ONLINE", null).stream()
        .filter(asset -> "SQL".equalsIgnoreCase(asset.taskType()))
        .filter(asset -> sameProject(asset.projectId(), datasetNode.projectId()))
        .map(this::toSnapshotIfValidSqlNode)
        .flatMap(Optional::stream)
        .sorted(Comparator
            .comparing(SourceSnapshot::nodeName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SourceSnapshot::nodeId))
        .toList();
  }

  private SourceSnapshot selectedSource(NodeDataset dataset) {
    if (dataset == null || dataset.currentVersion() == null) return null;
    try {
      TaskAsset asset = taskCatalogService.get(
          Long.parseLong(dataset.currentVersion().sourceTaskAssetId()));
      return toSnapshot(asset);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private TaskAsset requireSelectableSqlAsset(
      DevelopmentNode datasetNode,
      long sourceTaskAssetId) {
    if (sourceTaskAssetId <= 0L) {
      throw new IllegalArgumentException("sourceTaskAssetId 必须大于 0");
    }
    TaskAsset asset = taskCatalogService.get(sourceTaskAssetId);
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) {
      throw new IllegalArgumentException("Dataset 只能选择数据开发 SQL 来源");
    }
    if (!"SQL".equalsIgnoreCase(asset.taskType())) {
      throw new IllegalArgumentException("Dataset 当前仅支持 SQL 来源");
    }
    if (asset.status() != TaskAssetStatus.ONLINE) {
      throw new IllegalArgumentException("Dataset 只能选择 ONLINE SQL 来源");
    }
    if (!sameProject(asset.projectId(), datasetNode.projectId())) {
      throw new IllegalArgumentException("Dataset 只能选择同项目的 SQL 来源");
    }
    DevelopmentNode sourceNode = requireSourceSqlNode(asset);
    if (!sameProject(sourceNode.projectId(), datasetNode.projectId())) {
      throw new IllegalArgumentException("Dataset 只能选择同项目的 SQL 节点");
    }
    return asset;
  }

  private DevelopmentNode requireSourceSqlNode(TaskAsset asset) {
    long sourceNodeId;
    try {
      sourceNodeId = Long.parseLong(asset.sourceRef());
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("SQL TaskAsset sourceRef 不是有效节点 ID：" + asset.sourceRef());
    }
    DevelopmentNode sourceNode = nodeRepository.findById(sourceNodeId)
        .orElseThrow(() -> new IllegalStateException("SQL 来源节点不存在：" + sourceNodeId));
    if (!DevelopmentNodeType.SQL.name().equalsIgnoreCase(sourceNode.type())) {
      throw new IllegalStateException("TaskAsset 来源节点不是 SQL：" + sourceNodeId);
    }
    return sourceNode;
  }

  private Optional<SourceSnapshot> toSnapshotIfValidSqlNode(TaskAsset asset) {
    try {
      DevelopmentNode sourceNode = requireSourceSqlNode(asset);
      return Optional.of(toSnapshot(asset, sourceNode));
    } catch (RuntimeException exception) {
      return Optional.empty();
    }
  }

  private SourceSnapshot toSnapshot(TaskAsset asset) {
    DevelopmentNode sourceNode = nodeRepository.findById(parseSourceNodeId(asset)).orElse(null);
    return sourceNode == null
        ? new SourceSnapshot(
            asset.sourceRef(),
            asset.name(),
            String.valueOf(asset.id()),
            asset.status().name(),
            String.valueOf(asset.currentRevision().taskRevisionId()),
            asset.currentRevision().revisionNo())
        : toSnapshot(asset, sourceNode);
  }

  private SourceSnapshot toSnapshot(TaskAsset asset, DevelopmentNode sourceNode) {
    return new SourceSnapshot(
        String.valueOf(sourceNode.id()),
        sourceNode.name(),
        String.valueOf(asset.id()),
        asset.status().name(),
        String.valueOf(asset.currentRevision().taskRevisionId()),
        asset.currentRevision().revisionNo());
  }

  private long parseSourceNodeId(TaskAsset asset) {
    try {
      return Long.parseLong(asset.sourceRef());
    } catch (NumberFormatException exception) {
      return -1L;
    }
  }

  private DevelopmentNode requireDatasetNode(long nodeId) {
    if (nodeId <= 0L) throw new IllegalArgumentException("nodeId 必须大于 0");
    DevelopmentNode node = nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("数据开发节点不存在：" + nodeId));
    DevelopmentNodeType type = DevelopmentNodeType.tryParse(node.type()).orElse(null);
    if (type != DevelopmentNodeType.DATASET) {
      throw new IllegalArgumentException("当前节点不是 Dataset Node：" + nodeId);
    }
    return node;
  }

  private boolean sameProject(Long left, Long right) {
    return Objects.equals(normalizeProjectId(left), normalizeProjectId(right));
  }

  private Long normalizeProjectId(Long value) {
    return value == null || value <= 0L ? null : value;
  }

  public record DatasetNodeContext(
      String nodeId,
      String nodeName,
      boolean configured,
      List<SourceSnapshot> availableSources,
      SourceSnapshot selectedSource,
      NodeDataset dataset) {
  }

  public record SourceSnapshot(
      String nodeId,
      String nodeName,
      String taskAssetId,
      String status,
      String revisionId,
      Integer revisionNo) {
  }
}
