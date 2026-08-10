package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.common.bean.po.development.DevelopmentNodePO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for data-development tree node metadata. */
@Repository
public class DevelopmentNodeRepositoryAdapter implements DevelopmentNodeRepository {

  private static final long ROOT_DIRECTORY_ID = 0L;

  private final DevelopmentNodeMapper mapper;

  public DevelopmentNodeRepositoryAdapter(DevelopmentNodeMapper mapper) {
    this.mapper = mapper;
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
    return toDomain(po);
  }

  @Override
  public Optional<DevelopmentNode> findById(Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public List<DevelopmentNode> list() {
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentNodePO>()
                .orderByAsc(DevelopmentNodePO::getName)
                .orderByAsc(DevelopmentNodePO::getId))
        .stream()
        .map(this::toDomain)
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

  private Long toStoredDirectoryId(Long directoryId) {
    return directoryId == null || directoryId <= 0L ? ROOT_DIRECTORY_ID : directoryId;
  }

  private DevelopmentNode toDomain(DevelopmentNodePO po) {
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
        po.getUpdateTime());
  }
}
