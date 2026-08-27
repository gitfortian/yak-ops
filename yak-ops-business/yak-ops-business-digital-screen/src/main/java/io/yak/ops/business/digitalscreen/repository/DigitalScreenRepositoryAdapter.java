package io.yak.ops.business.digitalscreen.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.dao.mapper.DigitalScreenMapper;
import io.yak.ops.business.digitalscreen.dao.model.DigitalScreenPO;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenStatus;
import io.yak.ops.business.digitalscreen.repository.codec.DigitalScreenBindingsCodec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus persistence adapter for Digital Screen definitions. */
@Repository
@DependsOn("yakDigitalScreenFlyway")
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenRepositoryAdapter implements DigitalScreenRepository {

  private final DigitalScreenMapper mapper;
  private final DigitalScreenBindingsCodec bindingsCodec;

  @Override
  public List<DigitalScreen> list() {
    return mapper.selectList(
            Wrappers.<DigitalScreenPO>lambdaQuery()
                .orderByDesc(DigitalScreenPO::getUpdateTime)
                .orderByDesc(DigitalScreenPO::getId))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<DigitalScreen> findById(long id) {
    return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
  }

  @Override
  public DigitalScreen insert(
      String name,
      String description,
      String templateId,
      int templateVersion,
      Map<String, Object> bindings) {
    Instant now = Instant.now();
    DigitalScreenPO row = new DigitalScreenPO();
    row.setName(name);
    row.setDescription(description);
    row.setTemplateId(templateId);
    row.setTemplateVersion(templateVersion);
    row.setStatus(DigitalScreenStatus.DRAFT.name());
    row.setBindingsJson(bindingsCodec.encode(bindings));
    row.setCreateTime(Timestamp.from(now));
    row.setUpdateTime(Timestamp.from(now));
    if (mapper.insert(row) != 1 || row.getId() == null) {
      throw new IllegalStateException("创建数字化大屏失败");
    }
    return toDomain(row);
  }

  @Override
  public DigitalScreen update(
      long id,
      String name,
      String description,
      Map<String, Object> bindings) {
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getName, name)
            .set(DigitalScreenPO::getDescription, description)
            .set(DigitalScreenPO::getBindingsJson, bindingsCodec.encode(bindings))
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(Instant.now())));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public DigitalScreen updateStatus(
      long id,
      DigitalScreenStatus status,
      Instant publishedTime) {
    Instant now = Instant.now();
    int updated = mapper.update(
        null,
        Wrappers.<DigitalScreenPO>lambdaUpdate()
            .eq(DigitalScreenPO::getId, id)
            .set(DigitalScreenPO::getStatus, status.name())
            .set(
                DigitalScreenPO::getPublishedTime,
                publishedTime == null ? null : Timestamp.from(publishedTime))
            .set(DigitalScreenPO::getUpdateTime, Timestamp.from(now)));
    requireUpdated(updated, id);
    return required(id);
  }

  @Override
  public boolean deleteById(long id) {
    return mapper.deleteById(id) == 1;
  }

  private DigitalScreen required(long id) {
    return findById(id).orElseThrow(() -> notFound(id));
  }

  private void requireUpdated(int count, long id) {
    if (count != 1) throw notFound(id);
  }

  private IllegalArgumentException notFound(long id) {
    return new IllegalArgumentException("数字化大屏不存在或已被删除：" + id);
  }

  private DigitalScreen toDomain(DigitalScreenPO row) {
    return new DigitalScreen(
        row.getId(),
        row.getName(),
        row.getDescription(),
        row.getTemplateId(),
        row.getTemplateVersion() == null ? 1 : row.getTemplateVersion(),
        DigitalScreenStatus.valueOf(row.getStatus()),
        bindingsCodec.decode(row.getBindingsJson()),
        instant(row.getPublishedTime()),
        instant(row.getCreateTime()),
        instant(row.getUpdateTime()));
  }

  private Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
