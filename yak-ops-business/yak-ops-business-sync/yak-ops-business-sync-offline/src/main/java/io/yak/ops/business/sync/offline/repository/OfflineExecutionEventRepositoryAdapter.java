package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** 执行事件 PO 与领域模型之间的持久化适配器；Project 从所属 Execution 继承。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineExecutionEventRepositoryAdapter implements OfflineExecutionEventRepository {
  private final OfflineExecutionEventDao dao;
  private final OfflineJobExecutionRepository executionRepository;

  @Override
  public void append(OfflineExecutionEvent event) {
    if (event == null || event.getExecutionId() == null) {
      throw new IllegalArgumentException("执行事件缺少 executionId");
    }
    requireExecution(event.getExecutionId());
    OfflineExecutionEventPO po = new OfflineExecutionEventPO();
    BeanUtils.copyProperties(event, po);
    if (!dao.insert(po)) throw new IllegalStateException("记录离线同步执行事件失败");
    event.setId(po.getId());
  }

  @Override
  public List<OfflineExecutionEvent> list(Long executionId) {
    requireExecution(executionId);
    return dao.selectByExecutionId(executionId).stream().map(this::toDomain).toList();
  }

  @Override
  public List<OfflineExecutionEvent> listAfter(Long executionId, long afterId, int limit) {
    requireExecution(executionId);
    if (afterId < 0L) throw new IllegalArgumentException("事件游标不能为负数");
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("日志 limit 必须在 1 到 1000 之间");
    return dao.selectAfter(executionId, afterId, limit).stream().map(this::toDomain).toList();
  }

  private void requireExecution(Long executionId) {
    if (executionId == null || executionId <= 0L) {
      throw new IllegalArgumentException("executionId 必须大于 0");
    }
    executionRepository.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("离线同步执行实例不存在：" + executionId));
  }

  private OfflineExecutionEvent toDomain(OfflineExecutionEventPO po) {
    OfflineExecutionEvent value = new OfflineExecutionEvent();
    BeanUtils.copyProperties(po, value);
    return value;
  }
}
