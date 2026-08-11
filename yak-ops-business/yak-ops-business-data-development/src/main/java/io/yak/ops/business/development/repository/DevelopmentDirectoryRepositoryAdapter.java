package io.yak.ops.business.development.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.yak.ops.business.development.dao.mapper.DevelopmentDirectoryMapper;
import io.yak.ops.business.development.domain.DevelopmentDirectory;
import io.yak.ops.common.bean.po.development.DevelopmentDirectoryPO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for hierarchical data-development directories. */
@Repository
public class DevelopmentDirectoryRepositoryAdapter implements DevelopmentDirectoryRepository {

  private static final long ROOT_PARENT_ID = 0L;

  private final DevelopmentDirectoryMapper mapper;

  public DevelopmentDirectoryRepositoryAdapter(DevelopmentDirectoryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public DevelopmentDirectory insert(Long parentId, String name) {
    Instant now = Instant.now();
    DevelopmentDirectoryPO po = new DevelopmentDirectoryPO();
    po.setParentId(toStoredParentId(parentId));
    po.setName(name);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public Optional<DevelopmentDirectory> findById(Long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public List<DevelopmentDirectory> list() {
    return mapper.selectList(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .orderByAsc(DevelopmentDirectoryPO::getName)
                .orderByAsc(DevelopmentDirectoryPO::getId))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public boolean existsByName(Long parentId, String name) {
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getParentId, toStoredParentId(parentId))
                .eq(DevelopmentDirectoryPO::getName, name))
        > 0L;
  }

  @Override
  public boolean hasChildren(Long id) {
    return mapper.selectCount(
            new LambdaQueryWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getParentId, id))
        > 0L;
  }

  @Override
  public boolean updateName(Long id, String name) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<DevelopmentDirectoryPO>()
                .eq(DevelopmentDirectoryPO::getId, id)
                .set(DevelopmentDirectoryPO::getName, name)
                .set(DevelopmentDirectoryPO::getUpdateTime, Instant.now()))
        > 0;
  }

  @Override
  public boolean deleteById(Long id) {
    return mapper.deleteById(id) > 0;
  }

  private Long toStoredParentId(Long parentId) {
    return parentId == null || parentId <= 0L ? ROOT_PARENT_ID : parentId;
  }

  private DevelopmentDirectory toDomain(DevelopmentDirectoryPO po) {
    Long parentId = po.getParentId() == null || po.getParentId() == ROOT_PARENT_ID
        ? null
        : po.getParentId();
    return new DevelopmentDirectory(
        po.getId(),
        parentId,
        po.getName(),
        null,
        po.getCreateTime(),
        po.getUpdateTime());
  }
}
