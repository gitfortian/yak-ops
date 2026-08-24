package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceCallLogReader {
  private final DataServiceCallLogRepository repository;
  public List<InvocationRecord> recent() { return repository.recent(200); }
}
