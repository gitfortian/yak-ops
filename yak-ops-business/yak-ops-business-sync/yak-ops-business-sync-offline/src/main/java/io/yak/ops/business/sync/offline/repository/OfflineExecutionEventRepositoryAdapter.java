package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionEvent;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;

/** 执行事件 PO 与领域模型之间的持久化适配器。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineExecutionEventRepositoryAdapter implements OfflineExecutionEventRepository {
  private final OfflineExecutionEventDao dao;

  @Override
  public void append(OfflineExecutionEvent event) {
    OfflineExecutionEventPO po = new OfflineExecutionEventPO();
    BeanUtils.copyProperties(event, po);
    if (!dao.insert(po)) throw new IllegalStateException("记录离线同步执行事件失败");
    event.setId(po.getId());
  }

  @Override
  public List<OfflineExecutionEvent> list(Long executionId) {
    return dao.selectByExecutionId(executionId).stream().map(this::toDomain).toList();
  }

  @Override
  public List<OfflineExecutionEvent> listAfter(Long executionId, long afterId, int limit) {
    if (afterId < 0L) throw new IllegalArgumentException("事件游标不能为负数");
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("日志 limit 必须在 1 到 1000 之间");
    return dao.selectAfter(executionId, afterId, limit).stream().map(this::toDomain).toList();
  }

  private OfflineExecutionEvent toDomain(OfflineExecutionEventPO po) {
    OfflineExecutionEvent value = new OfflineExecutionEvent();
    BeanUtils.copyProperties(po, value);
    return value;
  }
}
