package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.common.bean.po.development.DevelopmentNodePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for data-development tree node metadata. */
@Repository
public class DevelopmentNodeRepositoryAdapter implements DevelopmentNodeRepository {

  private static final long ROOT_DIRECTORY_ID = 0L;

  private final DevelopmentNodeMapper mapper;
  private final JdbcTemplate jdbcTemplate;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentNodeRepositoryAdapter(
      DevelopmentNodeMapper mapper,
      JdbcTemplate jdbcTemplate,
      CurrentProject currentProject) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  public DevelopmentNodeRepositoryAdapter(
      DevelopmentNodeMapper mapper, JdbcTemplate jdbcTemplate) {
    this(mapper, jdbcTemplate, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public DevelopmentNode insert(
      String name,
      String type,
      Long ignoredProjectId,
      Long directoryId,
      boolean configured) {
    Long projectId = requiredProjectId();
    Instant now = Instant.now();
    DevelopmentNodePO po = new DevelopmentNodePO();
    po.setName(name);
    po.setType(type);
    po.setProjectId(projectId);
    po.setDirectoryId(toStoredDirectoryId(directoryId));
    po.setConfigured(configured);
    po.setDeleted(false);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po, false);
  }

  @Override
  public Optional<DevelopmentNode> findById(Long id) {
    Long projectId = requiredProjectId();
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<DevelopmentNodePO>()
                    .eq(DevelopmentNodePO::getId, id)
                    .eq(DevelopmentNodePO::getProjectId, projectId)))
        .map(po -> toDomain(po, hasUnpublishedChanges(po.getId())));
  }

  @Override
  public List<DevelopmentNode> list() {
    Long projectId = requiredProjectId();
    List<DevelopmentNodePO> nodes = mapper.selectList(
        new LambdaQueryWrapper<DevelopmentNodePO>()
            .eq(DevelopmentNodePO::getProjectId, projectId)
            .orderByAsc(DevelopmentNodePO::getName)
            .orderByAsc(DevelopmentNodePO::getId));
    Map<Long, Boolean> pendingPublishByNodeId = loadPendingPublishByNodeId(projectId);
    return nodes.stream()
        .map(po -> toDomain(po, Boolean.TRUE.equals(pendingPublishByNodeId.get(po.getId()))))
        .toList();
  }

  @Override
  public long count() {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
        new LambdaQueryWrapper<DevelopmentNodePO>()
            .eq(DevelopmentNodePO::getProjectId, projectId));
  }

  @Override
  public boolean existsByName(Long directoryId, String name) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId))
                .eq(DevelopmentNodePO::getName, name))
        > 0L;
  }

  @Override
  public boolean existsInDirectory(Long directoryId) {
    Long projectId = requiredProjectId();
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId)))
        > 0L;
  }

  @Override
  public boolean updateName(Long id, String name) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getName, name)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateConfigured(Long id, boolean configured) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getConfigured, configured)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateUpdatedBy(Long id, String updatedBy) {
    Long projectId = requiredProjectId();
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId)
                .set(DevelopmentNodePO::getUpdatedBy, updatedBy))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    Long projectId = requiredProjectId();
    return mapper.delete(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .eq(DevelopmentNodePO::getProjectId, projectId))
        > 0;
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long toStoredDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? ROOT_DIRECTORY_ID : directoryId;
  }

  private Map<Long, Boolean> loadPendingPublishByNodeId(Long projectId) {
    Map<Long, Boolean> result = new HashMap<>();
    String sql = "SELECT d.node_id, d.draft_revision, MAX(r.source_draft_revision) AS published_draft_revision "
        + "FROM yak_dev_task_draft d "
        + "JOIN yak_dev_node n ON n.id = d.node_id "
        + "LEFT JOIN yak_dev_task_revision r ON r.node_id = d.node_id "
        + "WHERE n.project_id = ? "
        + "GROUP BY d.node_id, d.draft_revision";
    jdbcTemplate.query(
        sql,
        rs -> {
          long nodeId = rs.getLong("node_id");
          long draftRevision = rs.getLong("draft_revision");
          long publishedDraftRevision = rs.getLong("published_draft_revision");
          boolean hasPublishedRevision = !rs.wasNull();
          result.put(nodeId, !hasPublishedRevision || draftRevision > publishedDraftRevision);
        },
        projectId);
    return result;
  }

  private boolean hasUnpublishedChanges(Long nodeId) {
    Long draftRevision = jdbcTemplate.queryForObject(
        "SELECT MAX(draft_revision) FROM yak_dev_task_draft WHERE node_id = ?",
        Long.class,
        nodeId);
    if (draftRevision == null) return false;

    Long publishedDraftRevision = jdbcTemplate.queryForObject(
        "SELECT MAX(source_draft_revision) FROM yak_dev_task_revision WHERE node_id = ?",
        Long.class,
        nodeId);
    return publishedDraftRevision == null || draftRevision > publishedDraftRevision;
  }

  private DevelopmentNode toDomain(DevelopmentNodePO po, boolean pendingPublish) {
    Long directoryId = po.getDirectoryId() == null || po.getDirectoryId() == ROOT_DIRECTORY_ID
        ? null
        : po.getDirectoryId();
    return new DevelopmentNode(
        po.getId(),
        po.getName(),
        po.getType(),
        po.getProjectId(),
        directoryId,
        Boolean.TRUE.equals(po.getConfigured()),
        po.getCreateTime(),
        po.getUpdateTime(),
        po.getUpdatedBy(),
        pendingPublish);
  }
}
