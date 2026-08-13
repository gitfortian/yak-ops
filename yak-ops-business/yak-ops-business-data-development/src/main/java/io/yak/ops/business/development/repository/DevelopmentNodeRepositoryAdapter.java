package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.common.bean.po.development.DevelopmentNodePO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for data-development tree node metadata. */
@Repository
public class DevelopmentNodeRepositoryAdapter implements DevelopmentNodeRepository {

  private static final long ROOT_DIRECTORY_ID = 0L;

  private final DevelopmentNodeMapper mapper;
  private final JdbcTemplate jdbcTemplate;

  public DevelopmentNodeRepositoryAdapter(DevelopmentNodeMapper mapper, JdbcTemplate jdbcTemplate) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public DevelopmentNode insert(
      String name,
      String type,
      Long projectId,
      Long directoryId,
      boolean configured) {
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
    return Optional.ofNullable(mapper.selectById(id))
        .map(po -> toDomain(po, hasUnpublishedChanges(po.getId())));
  }

  @Override
  public List<DevelopmentNode> list() {
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .orderByAsc(DevelopmentNodePO::getName)
                .orderByAsc(DevelopmentNodePO::getId))
        .stream()
        .map(po -> toDomain(po, hasUnpublishedChanges(po.getId())))
        .toList();
  }

  @Override
  public boolean existsByName(Long directoryId, String name) {
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId))
                .eq(DevelopmentNodePO::getName, name))
        > 0L;
  }

  @Override
  public boolean existsInDirectory(Long directoryId) {
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getDirectoryId, toStoredDirectoryId(directoryId)))
        > 0L;
  }

  @Override
  public boolean updateName(Long id, String name) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .set(DevelopmentNodePO::getName, name)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateConfigured(Long id, boolean configured) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .set(DevelopmentNodePO::getConfigured, configured)
                .set(DevelopmentNodePO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean updateUpdatedBy(Long id, String updatedBy) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentNodePO>()
                .eq(DevelopmentNodePO::getId, id)
                .set(DevelopmentNodePO::getUpdatedBy, updatedBy))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    return mapper.deleteById(id) > 0;
  }

  private Long toStoredDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? ROOT_DIRECTORY_ID : directoryId;
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
