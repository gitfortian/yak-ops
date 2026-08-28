package io.yak.ops.business.dataservice.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceCallLogMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceCallLogPO;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceCallLogRepositoryAdapter implements DataServiceCallLogRepository {

  private final DataServiceCallLogMapper mapper;

  @Override
  public InvocationRecord save(InvocationRecord record) {
    DataServiceCallLogPO po = toPo(record);
    mapper.insert(po);
    return toDomain(po);
  }

  @Override
  public List<InvocationRecord> recent(int limit) {
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public List<InvocationRecord> recentByApi(Long apiId, int limit) {
    if (apiId == null || apiId <= 0L) {
      throw new IllegalArgumentException("数据服务 ID 必须大于 0");
    }
    int size = normalizeLimit(limit);
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .eq(DataServiceCallLogPO::getApiId, apiId)
                .orderByDesc(DataServiceCallLogPO::getCreateTime)
                .orderByDesc(DataServiceCallLogPO::getId)
                .last("LIMIT " + size))
        .stream().map(this::toDomain).toList();
  }

  @Override
  public List<InvocationRecord> between(LocalDateTime from, LocalDateTime to) {
    return mapper.selectList(
            Wrappers.<DataServiceCallLogPO>lambdaQuery()
                .ge(from != null, DataServiceCallLogPO::getCreateTime, from)
                .le(to != null, DataServiceCallLogPO::getCreateTime, to)
                .orderByAsc(DataServiceCallLogPO::getCreateTime)
                .orderByAsc(DataServiceCallLogPO::getId))
        .stream().map(this::toDomain).toList();
  }

  private int normalizeLimit(int limit) {
    return Math.max(1, Math.min(1_000, limit));
  }

  private InvocationRecord toDomain(DataServiceCallLogPO po) {
    return new InvocationRecord(
        po.getId(), po.getApiId(), po.getServiceName(), po.getServicePath(), po.getCallerType(),
        po.getApiKeyId(), po.getApiKeyName(), po.getApiKeyPrefix(), po.getParamsJson(),
        Boolean.TRUE.equals(po.getSuccess()), value(po.getDurationMs()), value(po.getRowCount()),
        po.getErrorMessage(), po.getCreateTime());
  }

  private DataServiceCallLogPO toPo(InvocationRecord record) {
    DataServiceCallLogPO po = new DataServiceCallLogPO();
    po.setId(record.id());
    po.setApiId(record.apiId());
    po.setServiceName(record.serviceName());
    po.setServicePath(record.servicePath());
    po.setCallerType(record.callerType());
    po.setApiKeyId(record.apiKeyId());
    po.setApiKeyName(record.apiKeyName());
    po.setApiKeyPrefix(record.apiKeyPrefix());
    po.setParamsJson(record.paramsJson());
    po.setSuccess(record.success());
    po.setDurationMs(record.durationMs());
    po.setRowCount(record.rowCount());
    po.setErrorMessage(record.errorMessage());
    po.setCreateTime(record.createTime());
    return po;
  }

  private long value(Long value) { return value == null ? 0L : Math.max(0L, value); }
  private int value(Integer value) { return value == null ? 0 : Math.max(0, value); }
}
