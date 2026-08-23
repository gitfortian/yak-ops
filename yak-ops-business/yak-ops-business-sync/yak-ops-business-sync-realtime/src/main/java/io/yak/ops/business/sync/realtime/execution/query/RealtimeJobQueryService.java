package io.yak.ops.business.sync.realtime.execution.query;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobPage;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime task and execution read models. */
@Service("realtimeJobQueryApplicationService")
public class RealtimeJobQueryService {

  private final io.yak.ops.business.sync.realtime.service.RealtimeJobQueryService query;
  private final RealtimeJobService jobs;

  public RealtimeJobQueryService(
      io.yak.ops.business.sync.realtime.service.RealtimeJobQueryService query,
      RealtimeJobService jobs) {
    this.query = query;
    this.jobs = jobs;
  }

  public RealtimeJobView detail(long id) {
    return jobs.get(id);
  }

  public RealtimeJobPage page(
      int pageNo,
      int pageSize,
      String keyword,
      Long id,
      String releaseState,
      String stateGroup) {
    return query.page(pageNo, pageSize, keyword, id, releaseState, stateGroup);
  }
}
